package jp.akkiserver.rc.core.machine;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureFactory;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;
import jp.akkiserver.rc.core.architecture.ExecutionResult;
import jp.akkiserver.rc.core.device.EepromDevice;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTest {
    @Test
    void hostActionsWaitUntilGuestWorkerHasReturned() {
        Fixture f = new Fixture();
        f.boot();
        boolean[] called = {false};
        var action = f.machine.requestHostAction(() -> called[0] = true);
        f.machine.tick();
        assertFalse(called[0]);
        assertFalse(action.isDone());
        f.workers.step();
        f.machine.tick();
        action.join();
        assertTrue(called[0]);
    }

    @Test
    void fatalHostFailureIsSavedAndReportedOnTheNextBootOnly() {
        Fixture f = new Fixture();
        f.language.run =
            () -> {
                throw new AssertionError("broken host");
            };
        f.boot();
        f.slice();
        assertEquals(Machine.State.FAILED, f.machine.state());
        var saved = f.machine.requestSnapshot();
        f.machine.tick();
        assertEquals(StdErrors.E_MCHK, saved.join().data().getInt("pendingResetReason"));
        var off = f.machine.requestPowerOff();
        f.machine.tick();
        f.workers.step();
        f.machine.tick();
        off.join();
        f.language.run = () -> ExecutionResult.waiting(0);
        f.boot();
        assertEquals(StdErrors.E_MCHK, f.language.host.resetReason());
        off = f.machine.requestPowerOff();
        f.slice();
        f.workers.step();
        f.machine.tick();
        off.join();
        f.boot();
        assertEquals(0, f.language.host.resetReason());
    }

    @Test
    void poweredOffStateAndDeviceDataCanBeSavedWithoutARuntime() throws Exception {
        Fixture f = new Fixture();
        Counter counter = new Counter();
        counter.value = 9;
        f.machine.requestAttach(1, counter);
        f.machine.tick();
        var save = f.machine.requestSnapshot();
        f.machine.tick();
        MachineSnapshot saved = MachineSnapshot.read(save.join().write());
        assertFalse(saved.data().getBoolean("powered"));
        f.machine.requestDetach(1);
        f.machine.tick();
        counter.value = 1;
        f.machine.requestAttach(1, counter);
        f.machine.tick();
        var load = f.machine.requestRestore(saved);
        f.machine.tick();
        load.join();
        assertEquals(9, counter.value);
        assertEquals(Machine.State.OFF, f.machine.state());
    }

    @Test
    void doesNotWaitOrAccumulateBudgetAndStopsOnlyAfterWorkerReturns() {
        Fixture f = new Fixture();
        f.boot();
        var off = f.machine.requestPowerOff();
        for (int i = 0; i < 20; i++) f.machine.tick();
        assertFalse(off.isDone());
        assertEquals(1, f.workers.tasks.size());
        assertEquals(0, f.language.runs);
        f.workers.step();
        f.machine.tick();
        assertEquals(123, f.language.budget);
        assertEquals(0, f.language.closes);
        assertEquals(Machine.State.STOPPING, f.machine.state());
        f.workers.step();
        f.machine.tick();
        assertTrue(off.isDone());
        assertEquals(1, f.language.closes);
        assertEquals(Machine.State.OFF, f.machine.state());
    }

    @Test
    void completedEntryPowersOffAndRebootCreatesNewGuest() {
        Fixture f = new Fixture();
        f.language.run = ExecutionResult::stopped;
        f.boot();
        f.slice();
        f.workers.step();
        f.machine.tick();
        assertEquals(Machine.State.OFF, f.machine.state());
        var reboot = f.machine.requestReboot();
        f.machine.tick();
        assertTrue(reboot.isCompletedExceptionally()); // no physical boot source on an off machine
        f.machine.requestAttach(0, new EepromDevice(UUID.randomUUID(), new byte[]{42}));
        f.machine.tick();
        var on = f.machine.requestPowerOn();
        f.machine.tick();
        f.workers.step();
        f.machine.tick();
        assertTrue(on.isDone());
    }

    @Test
    void asyncCallContinuesGuestThenRunsOnTickAndSignalsCompletion() {
        Fixture f = new Fixture();
        Counter counter = new Counter();
        f.machine.requestAttach(1, counter);
        f.machine.tick();
        int[] ticket = {0};
        f.language.run =
            () -> {
                if (ticket[0] == 0) ticket[0] = f.language.host.submit(1, 0, new Object[]{7});
                return ExecutionResult.waiting(0);
            };
        f.boot();
        f.slice();
        assertTrue(ticket[0] > 0);
        assertEquals(7, counter.value);
        assertSame(Thread.currentThread(), counter.calledOn);
        assertEquals(7, f.language.host.result(ticket[0]).value());
        assertEquals(MachineEvent.COMPLETE, f.language.host.pollEvent().kind());
        f.language.host.releaseResult(ticket[0]);
        assertEquals(StdErrors.E_BADHANDLE, f.language.host.result(ticket[0]).status());
    }

    @Test
    void detachedSlotCannotRedirectAnOldRequestOrSignal() {
        Fixture f = new Fixture();
        Counter old = new Counter(), replacement = new Counter();
        f.machine.requestAttach(1, old);
        f.machine.tick();
        f.boot();
        f.language.run =
            () -> {
                f.language.host.submit(1, 0, new Object[]{3});
                return ExecutionResult.waiting(0);
            };
        f.machine.requestDetach(1);
        f.machine.requestAttach(1, replacement);
        f.slice();
        assertEquals(0, old.value);
        assertEquals(0, replacement.value);
        assertEquals(StdErrors.E_NODEV, f.language.host.result(1).status());
        while (f.language.host.pollEvent() != null) {
        }
        old.context.signal(new byte[]{9});
        assertNull(f.language.host.peekEvent());
    }

    @Test
    void indefiniteWaitSleepsAndEventWakesWithoutConsumingIt() {
        Fixture f = new Fixture();
        Counter counter = new Counter();
        f.machine.requestAttach(1, counter);
        f.machine.tick();
        f.language.run = () -> ExecutionResult.waiting(0);
        f.boot();
        f.slice();
        assertEquals(Machine.State.WAITING, f.machine.state());
        assertTrue(f.workers.tasks.isEmpty());
        f.machine.tick();
        assertTrue(f.workers.tasks.isEmpty());
        byte[] signal = {4};
        counter.context.signal(signal);
        signal[0] = 99;
        f.machine.tick();
        assertEquals(Machine.State.RUNNING, f.machine.state());
        assertTrue(f.language.host.wokeByEvent());
        assertArrayEquals(new byte[]{4}, f.language.host.peekEvent().payload());
    }

    @Test
    void snapshotCopiesHostAndRuntimeAndRestoresTheSameDevice() throws Exception {
        Fixture f = new Fixture();
        Counter counter = new Counter();
        f.machine.requestAttach(1, counter);
        f.machine.tick();
        counter.value = 17;
        f.boot();
        var save = f.machine.requestSnapshot();
        f.slice();
        f.workers.step();
        f.machine.tick();
        MachineSnapshot snapshot = MachineSnapshot.read(save.join().write());
        var off = f.machine.requestPowerOff();
        f.slice();
        f.workers.step();
        f.machine.tick();
        off.join();
        counter.value = 2;
        var restore = f.machine.requestRestore(snapshot);
        f.machine.tick();
        f.workers.step();
        f.machine.tick();
        restore.join();
        assertEquals(17, counter.value);
        assertEquals(1, f.language.restores);
        assertEquals(Machine.State.RUNNING, f.machine.state());
    }

    @Test
    void overflowPreservesOldEventsAndReportsLossFirst() {
        EventQueue queue = new EventQueue(1);
        queue.add(new MachineEvent(4, 0, 0, 1, new byte[]{1}));
        queue.add(new MachineEvent(4, 0, 0, 2, new byte[]{2}));
        assertEquals(MachineEvent.OVERFLOW, queue.peek().kind());
        assertEquals(1, queue.poll().parameter());
        assertArrayEquals(new byte[]{1}, queue.poll().payload());
        assertNull(queue.poll());
    }

    static final class Workers implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        public void execute(Runnable task) {
            tasks.add(task);
        }

        void step() {
            Objects.requireNonNull(tasks.poll()).run();
        }
    }

    static final class Language implements ArchitectureFactory {
        ArchitectureHost host;
        int runs, closes, boots, restores;
        long budget;
        Supplier<ExecutionResult> run = ExecutionResult::yielded;

        public String id() {
            return "test-language";
        }

        public Architecture create(ArchitectureHost host) {
            this.host = host;
            return new Architecture() {
                public void boot(byte[] bytes) {
                    assertArrayEquals(new byte[]{42}, bytes);
                    boots++;
                }

                public ExecutionResult run(long limit) {
                    budget = limit;
                    runs++;
                    return run.get();
                }

                public byte[] snapshot() {
                    return new byte[]{(byte) runs};
                }

                public void restore(byte[] image, byte[] saved) {
                    restores++;
                    runs = saved[0];
                }

                public void close() {
                    closes++;
                }
            };
        }
    }

    @Module
    static final class Counter implements Device {
        final UUID id = UUID.randomUUID();
        int value;
        DeviceContext context;
        Thread calledOn;

        public DeviceInfo info() {
            return new DeviceInfo(1, 0, 0, id);
        }

        public void attached(DeviceContext context) {
            this.context = context;
        }

        @Export(async = true)
        public int add(int amount) {
            calledOn = Thread.currentThread();
            return value += amount;
        }

        @Export
        public int get() {
            return value;
        }

        public void save(DataCompound out) {
            out.putInt("value", value);
        }

        public void load(DataCompound in) {
            value = in.getInt("value");
        }
    }

    record Fixture(Workers workers, Language language, Machine machine) {
        Fixture() {
            this(new Workers(), new Language());
        }

        Fixture(Workers workers, Language language) {
            this(
                workers,
                language,
                new Machine(new MachineConfig(4, 123, 8, 4), language, workers));
        }

        void boot() {
            var future = machine.requestPowerOn(new byte[]{42});
            machine.tick();
            assertFalse(future.isDone());
            workers.step();
            machine.tick();
            assertTrue(future.isDone());
            assertEquals(Machine.State.RUNNING, machine.state());
        }

        void slice() {
            workers.step();
            machine.tick();
        }
    }
}
