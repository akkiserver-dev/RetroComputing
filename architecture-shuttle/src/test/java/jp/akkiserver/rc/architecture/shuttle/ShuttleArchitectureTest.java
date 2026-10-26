package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureException;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;
import jp.akkiserver.rc.core.architecture.ExecutionResult;
import jp.akkiserver.rc.core.device.EepromDevice;
import jp.akkiserver.rc.core.device.KeyboardDevice;
import jp.akkiserver.rc.core.machine.Machine;
import jp.akkiserver.rc.core.machine.MachineConfig;
import jp.akkiserver.rc.core.machine.MachineEvent;
import jp.akkiserver.rc.core.machine.MachineSnapshot;
import jp.akkiserver.rc.core.machine.MachineWorkers;
import jp.akkiserver.rc.core.runtime.CallResult;
import jp.akkiserver.rc.core.runtime.MethodInvoker;
import jp.akkiserver.rc.core.runtime.MethodTable;
import jp.akkiserver.rc.core.storage.MemoryStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ShuttleArchitectureTest {
    private final List<ShuttleArchitectureFactory> factories = new ArrayList<>();

    private static void requireEqual(Wasm wasm, boolean wide) {
        // Trap if a compiled guest binding supplied the wrong ABI layout or value.
        wasm.op(wide ? 0x52 : 0x47, 0x04, 0x40, 0x00, 0x0b);
    }

    private static void until(Machine machine, BooleanSupplier condition)
        throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline)
                fail(
                    "Machine did not reach the expected state: "
                        + machine.state()
                        + ", "
                        + machine.failure());
            machine.tick();
            Thread.sleep(1);
        }
    }

    static Wasm logging() {
        return new Wasm()
            .imp("fw", "log", "ii>")
            .imp("fw", "yield", ">")
            .imp("fw", "shutdown", ">");
    }

    private static void resume(Wasm kernel, long result) {
        kernel.i(1).l(100).l(result).i(2048).call(2).op(0x1a);
    }

    private static void childByte(Wasm kernel) {
        kernel.i(1).i(0).i(4096).i(1).call(3).op(0x1a).i(4096).i(1).call(4);
    }

    @Test
    void cAndRustGuestBindingsMatchEveryNativeImport() throws Exception {
        var directory = Path.of("build/abi");
        Assumptions.assumeTrue(
            Files.exists(directory.resolve("c-parent.wasm"))
                && Files.exists(directory.resolve("rust-parent.wasm"))
                && Files.exists(directory.resolve("c-child.wasm"))
                && Files.exists(directory.resolve("rust-child.wasm")),
            "Run abi/test.ps1 to build C/Rust fixtures");
        for (String language : List.of("c", "rust")) {
            Host host = new Host();
            try (Architecture architecture = factory().create(host)) {
                architecture.boot(Files.readAllBytes(directory.resolve(language + "-parent.wasm")));
                assertEquals(ExecutionResult.stopped(), architecture.run(10000));
                assertEquals(List.of(language.equals("c") ? "C ABI OK" : "Rust ABI OK"), host.logs);
            }
            byte[] child = Files.readAllBytes(directory.resolve(language + "-child.wasm"));
            Wasm parent = new Wasm().imp("fw", "image_load", "ii>i")
                .imp("co", "co_create", "il>i").imp("co", "co_resume", "illi>i");
            parent.data = child;
            parent.i(0).i(child.length).call(0).l(4L << 20).call(1).op(0x1a);
            parent.i(1).l(10000).l(0).i(32768).call(2).i(1);
            requireEqual(parent, false);
            parent.i(32772).op(0x28, 2, 0).i(7);
            requireEqual(parent, false);
            for (int i = 0; i < 4; i++) {
                parent.i(32776 + i * 8).op(0x29, 3, 0).l(11L * (i + 1));
                requireEqual(parent, true);
            }
            parent.i(1).l(10000).l(123).i(32768).call(2).i(2);
            requireEqual(parent, false);
            try (Architecture architecture = factory().create(new Host())) {
                architecture.boot(parent.bytes());
                assertEquals(ExecutionResult.stopped(), architecture.run(100000));
            }
        }
    }

    @Test
    void javaErrorCrossesJniAsMachineCheck() throws Exception {
        Host host = new Host();
        host.table = MethodTable.create(new BrokenHost());
        Wasm wasm = new Wasm().imp("fw", "dev_call", "iiiiii>i");
        wasm.i(0).i(0).i(0).i(0).i(0).i(0).call(0).op(0x1a);
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(wasm.bytes());
            ArchitectureException error =
                assertThrows(ArchitectureException.class, () -> architecture.run(100));
            assertEquals(StdErrors.E_MCHK, error.resetReason());
        }
    }

    @Test
    void failedExecReturnsAnErrorAndKeepsTheOldGuestRunning() throws Exception {
        Wasm child = new Wasm().imp("sys", "call", "illll>l");
        child.i(0).l(0).l(0).l(0).l(0).call(0).op(0x1a);
        byte[] image = child.bytes();
        Wasm bios =
            new Wasm()
                .imp("fw", "image_load", "ii>i")
                .imp("fw", "exec", "iii>i")
                .imp("fw", "log", "ii>");
        bios.data = Arrays.copyOf(image, image.length + 1);
        bios.data[image.length] = 'K';
        bios.i(0).i(image.length).call(0).i(0).i(0).call(1).op(0x1a).i(image.length).i(1).call(2);
        Host host = new Host();
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(bios.bytes());
            assertEquals(ExecutionResult.stopped(), architecture.run(100));
            assertEquals(List.of("K"), host.logs);
        }
    }

    private ShuttleArchitectureFactory factory() {
        var factory = new ShuttleArchitectureFactory();
        factories.add(factory);
        return factory;
    }

    @Test
    @Timeout(15)
    void platformWorkerSavesSleepingGuestAndWakesAfterRestoring() throws Exception {
        Wasm wasm =
            new Wasm()
                .imp("fw", "log", "ii>")
                .imp("fw", "hlt", "l>i")
                .imp("fw", "shutdown", ">");
        wasm.data = new byte[]{'A', 'B'};
        wasm.i(0).i(1).call(0).l(0).call(1).op(0x1a).i(1).i(1).call(0).call(2);
        var factory = factory();
        var workers = MachineWorkers.create(1, 8L << 20);
        List<String> logs = new CopyOnWriteArrayList<>();
        Supplier<Machine> create =
            () ->
                new Machine(
                    MachineConfig.defaults(),
                    factory,
                    workers,
                    id -> new MemoryStorage(),
                    logs::add);
        UUID eeprom = UUID.randomUUID(), keyboard = UUID.randomUUID();
        Machine first = create.get(), second = null;
        try {
            first.requestAttach(
                0, new EepromDevice(eeprom, wasm.bytes()));
            first.requestAttach(1, new KeyboardDevice(keyboard));
            first.requestPowerOn();
            until(first, () -> first.state() == Machine.State.WAITING);
            var save = first.requestSnapshot();
            until(first, save::isDone);
            MachineSnapshot snapshot = MachineSnapshot.read(save.join().write());
            var off = first.requestPowerOff();
            until(first, off::isDone);
            off.join();
            second = create.get();
            var key = new KeyboardDevice(keyboard);
            second.requestAttach(
                0, new EepromDevice(eeprom, wasm.bytes()));
            second.requestAttach(1, key);
            var restore = second.requestRestore(snapshot);
            until(second, restore::isDone);
            restore.join();
            assertEquals(List.of("A"), logs);
            assertEquals(Machine.State.WAITING, second.state());
            key.key(3, 0, 0, 'x');
            Machine restored = second;
            until(second, () -> restored.state() == Machine.State.OFF);
            assertEquals(List.of("A", "B"), logs);
            assertNull(second.failure());
        } finally {
            for (Machine machine : new Machine[]{first, second})
                if (machine != null && machine.state() != Machine.State.OFF) {
                    var off = machine.requestPowerOff();
                    until(machine, off::isDone);
                }
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @AfterEach
    void closeFactories() throws Exception {
        for (var factory : factories)
            factory.close();
    }

    @Test
    void realGuestLogsYieldsRestoresAndShutsDownWithoutRepeatingEffects() throws Exception {
        Wasm wasm = logging();
        wasm.data = "AB".getBytes();
        wasm.i(0)
            .i(1)
            .call(0)
            .call(1)
            .i(1)
            .i(1)
            .call(0)
            .call(2)
            .op(0); // unreachable after shutdown
        Host host = new Host();
        byte[] saved;
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(wasm.bytes());
            assertEquals(ExecutionResult.Reason.YIELDED, architecture.run(100).reason());
            assertEquals(List.of("A"), host.logs);
            saved = architecture.snapshot();
        }
        try (Architecture architecture = factory().create(host)) {
            architecture.restore(wasm.bytes(), saved);
            assertEquals(ExecutionResult.Reason.STOPPED, architecture.run(100).reason());
            assertEquals(List.of("A", "B"), host.logs);
        }
    }

    @Test
    void instructionBudgetInterruptsInfiniteLoop() throws Exception {
        Wasm wasm = new Wasm().op(0x03, 0x40, 0x0c, 0, 0x0b);
        try (Architecture architecture = factory().create(new Host())) {
            architecture.boot(wasm.bytes());
            assertEquals(ExecutionResult.yielded(), architecture.run(3));
            assertEquals(ExecutionResult.yielded(), architecture.run(3));
        }
    }

    @Test
    void sizeQueryAndHeldReturnDoNotInvokeDeviceTwice() throws Exception {
        Counter counter = new Counter();
        Host host = new Host();
        host.table = MethodTable.create(counter);
        Wasm wasm =
            new Wasm()
                .imp("fw", "dev_call", "iiiiii>i")
                .imp("fw", "dev_result", "iii>i")
                .imp("fw", "log", "ii>");
        wasm.i(0).i(0).i(0).i(0).i(-1).i(0).call(0).op(0x1a);
        wasm.i(-1).i(0).i(4).call(1).op(0x1a); // fetch held result into first four memory bytes
        wasm.i(0).i(1).call(2);
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(wasm.bytes());
            assertEquals(ExecutionResult.stopped(), architecture.run(100));
        }
        assertEquals(1, counter.n);
        assertEquals("\u0001", host.logs.getFirst());
    }

    @Test
    void trapsAreReportedAsGuestFailure() throws Exception {
        try (Architecture architecture = factory().create(new Host())) {
            architecture.boot(new Wasm().op(0).bytes());
            ArchitectureException failure =
                assertThrows(ArchitectureException.class, () -> architecture.run(100));
            assertEquals(StdErrors.E_TRAP, failure.resetReason());
        }
    }

    @Test
    void machineCanRunARealEepromGuest() throws Exception {
        List<String> logs = new ArrayList<>();
        Wasm wasm = logging();
        wasm.data = new byte[]{'X'};
        wasm.i(0).i(1).call(0).call(1).call(2);
        Machine machine =
            new Machine(
                MachineConfig.defaults(),
                factory(),
                Runnable::run,
                id -> new MemoryStorage(),
                logs::add);
        machine.requestAttach(
            0, new EepromDevice(UUID.randomUUID(), wasm.bytes()));
        machine.tick();
        var on = machine.requestPowerOn();
        for (int i = 0; i < 12 && machine.state() != Machine.State.OFF || !on.isDone(); i++) {
            if (i > 20)
                fail("Machine did not stop");
            machine.tick();
        }
        on.join();
        assertEquals(List.of("X"), logs);
        assertEquals(Machine.State.OFF, machine.state());
        assertNull(machine.failure());
    }

    @Test
    void execReplacesBiosAndPassesBootArguments() throws Exception {
        Wasm os =
            new Wasm()
                .imp("fw", "boot_args", "ii>i")
                .imp("fw", "log", "ii>")
                .imp("fw", "shutdown", ">");
        os.i(0).i(8).call(0).op(0x1a).i(0).i(1).call(1).call(2);
        byte[] image = os.bytes();
        Wasm bios = new Wasm().imp("fw", "image_load", "ii>i").imp("fw", "exec", "iii>i");
        bios.data = Arrays.copyOf(image, image.length + 1);
        bios.data[image.length] = 'Q';
        bios.i(0).i(image.length).call(0).i(image.length).i(1).call(1).op(0x1a, 0);
        Host host = new Host();
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(bios.bytes());
            assertEquals(ExecutionResult.yielded(), architecture.run(100));
            assertEquals(ExecutionResult.stopped(), architecture.run(100));
            assertEquals(List.of("Q"), host.logs);
        }
    }

    @Test
    void childSyscallAndCompletedMemorySurviveMultipleRestores() throws Exception {
        Wasm child = new Wasm().imp("sys", "call", "illll>l");
        child.i(0).i(7).l(10).l(20).l(30).l(40).call(0).op(0x37, 3, 0);
        byte[] image = child.bytes();
        Wasm kernel =
            new Wasm()
                .imp("fw", "image_load", "ii>i")
                .imp("co", "co_create", "il>i")
                .imp("co", "co_resume", "illi>i")
                .imp("co", "co_peek", "iiii>i")
                .imp("fw", "log", "ii>")
                .imp("fw", "yield", ">");
        kernel.data = image;
        kernel.i(0).i(image.length).call(0).l(2L << 20).call(1).op(0x1a);
        resume(kernel, 0);
        kernel.i(2048).i(1).call(4).call(5);
        resume(kernel, 'Z');
        kernel.i(2048).i(1).call(4);
        childByte(kernel);
        kernel.call(5);
        childByte(kernel);
        Host host = new Host();
        byte[] saved;
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(kernel.bytes());
            assertEquals(ExecutionResult.yielded(), architecture.run(100));
            assertEquals(List.of("\u0001"), host.logs);
            saved = architecture.snapshot();
        }
        try (Architecture architecture = factory().create(host)) {
            architecture.restore(kernel.bytes(), saved);
            assertEquals(ExecutionResult.yielded(), architecture.run(100));
            assertEquals(List.of("\u0001", "\u0002", "Z"), host.logs);
            saved = architecture.snapshot();
        }
        try (Architecture architecture = factory().create(host)) {
            architecture.restore(kernel.bytes(), saved);
            assertEquals(ExecutionResult.stopped(), architecture.run(100));
            assertEquals(List.of("\u0001", "\u0002", "Z", "Z"), host.logs);
        }
    }

    @Test
    void childTrapDoesNotKillTheKernelAndMemoryLimitMakesGrowReturnMinusOne() throws Exception {
        Wasm child = new Wasm().op(0);
        byte[] image = child.bytes();
        Wasm kernel =
            new Wasm()
                .imp("fw", "image_load", "ii>i")
                .imp("co", "co_create", "il>i")
                .imp("co", "co_resume", "illi>i")
                .imp("fw", "log", "ii>");
        kernel.data = image;
        kernel.i(0).i(image.length).call(0).l(2L << 20).call(1).op(0x1a);
        resume(kernel, 0);
        kernel.i(2048).i(1).call(3);
        Host host = new Host();
        try (Architecture architecture = factory().create(host)) {
            architecture.boot(kernel.bytes());
            assertEquals(ExecutionResult.stopped(), architecture.run(100));
            assertEquals(List.of("\u0003"), host.logs);
        }
    }

    @Module
    static final class BrokenHost {
        @Export
        public void fail() {
            throw new AssertionError("device failure");
        }
    }

    static class Host implements ArchitectureHost {
        final List<String> logs = new ArrayList<>();
        long ticks;
        MethodTable table;

        public long time() {
            return ticks;
        }

        public int resetReason() {
            return 0;
        }

        public int slotCount() {
            return 1;
        }

        public DeviceInfo deviceInfo(int slot) {
            return null;
        }

        public MethodTable methods(int slot) {
            return slot == 0 ? table : null;
        }

        public CallResult invoke(int slot, int method, Object[] args) {
            return new MethodInvoker().invoke(table, method, args);
        }

        public int submit(int slot, int method, Object[] args) {
            return StdErrors.E_AGAIN;
        }

        public CallResult result(int ticket) {
            return CallResult.failure(StdErrors.E_BADHANDLE);
        }

        public void releaseResult(int ticket) {
        }

        public MachineEvent peekEvent() {
            return null;
        }

        public MachineEvent pollEvent() {
            return null;
        }

        public boolean wokeByEvent() {
            return true;
        }

        public void log(String message) {
            logs.add(message);
        }
    }

    @Module
    static class Counter {
        int n;

        @Export
        public int increment() {
            return ++n;
        }
    }
}
