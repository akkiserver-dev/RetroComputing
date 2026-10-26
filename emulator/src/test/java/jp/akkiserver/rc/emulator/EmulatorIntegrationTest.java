package jp.akkiserver.rc.emulator;

import jp.akkiserver.rc.core.machine.Machine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorIntegrationTest {
    static String text(byte[] cells) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cells.length; i += 3)
            result.append(
                (char) (Byte.toUnsignedInt(cells[i]) | Byte.toUnsignedInt(cells[i + 1]) << 8));
        return result.toString();
    }

    static void pump(EmulatorController controller, BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            controller.tick(f -> {
            });
            Thread.sleep(2);
        }
        assertNull(controller.machine.failure());
        assertTrue(done.getAsBoolean(), controller.status());
    }

    @Test
    void reloadRereadsTheBuiltWasmFile(@TempDir Path directory)
        throws Exception {
        Path file = directory.resolve("guest with spaces.wasm");
        try (var controller = new EmulatorController(null)) {
            pump(controller, () -> controller.machine.state() == Machine.State.WAITING);
            for (String text : List.of("first build", "second build")) {
                DemoWasm wasm = new DemoWasm().imp("fw", "log", "ii>");
                wasm.data = text.getBytes(StandardCharsets.UTF_8);
                wasm.i(0).i(wasm.data.length).call(0);
                Files.write(file, wasm.bytes());
                controller.reload(file);
                pump(controller, () -> controller.logs.contains(text));
            }
        }
    }

    @Test
    void closingDuringReloadDoesNotStartANewGuestAfterClosingRuntime() throws Exception {
        try (var controller = new EmulatorController(null)) {
            pump(controller, () -> controller.machine.state() == Machine.State.WAITING);
            controller.reload(null);
            controller.requestClose();
            controller.reload(null);
        }
    }

    @Test
    void realGuestDrawsAndReloadsAtSafeBoundary() throws Exception {
        try (var controller = new EmulatorController(null)) {
            pump(controller, () -> controller.machine.state() == Machine.State.WAITING);
            CompletableFuture<Void> capture =
                controller.machine.requestHostAction(
                    () -> {
                        var state = controller.video.captureDisplay();
                        assertEquals(80, state.mode().width());
                        // Request an ordinary video read on the safe host boundary.
                        assertTrue(
                            text(controller.video.read(
                                (short) 3,
                                (short) 2,
                                (short) 15,
                                (short) 1))
                                .contains("Retro"));
                    });
            pump(controller, capture::isDone);
            capture.join();
            controller.reload(null);
            pump(
                controller,
                () ->
                    controller.machine.state() == Machine.State.OFF
                        || controller.machine.state() == Machine.State.STOPPING);
            pump(controller, () -> controller.machine.state() == Machine.State.WAITING);
            assertTrue(controller.logs.stream().noneMatch(s -> s.startsWith("ERROR")));
        }
    }

    @Test
    void rustGuestEchoesKeyboardWhenBuilt() throws Exception {
        Path wasm = Path.of("build/rust-guest/wasm32-unknown-unknown/release/rc_screen_guest.wasm");
        Assumptions.assumeTrue(
            Files.exists(wasm), "Build with scripts/run-emulator.ps1 -RustExample first");
        try (var controller = new EmulatorController(wasm)) {
            pump(controller, () -> controller.machine.state() == Machine.State.WAITING);
            controller.keyboard.key(3, 0, 0, 'R');
            for (int i = 0; i < 30; i++) {
                controller.tick(f -> {
                });
                Thread.sleep(3);
            }
            var check =
                controller.machine.requestHostAction(
                    () ->
                        assertEquals(
                            "R",
                            text(
                                controller.video.read(
                                    (short) 3, (short) 8, (short) 1,
                                    (short) 1))));
            pump(controller, check::isDone);
            check.join();
            controller.keyboard.key(1, 0, 259, 0);
            for (int i = 0; i < 30; i++) {
                controller.tick(f -> {
                });
                Thread.sleep(3);
            }
            var erased =
                controller.machine.requestHostAction(
                    () ->
                        assertEquals(
                            " ",
                            text(
                                controller.video.read(
                                    (short) 3, (short) 8, (short) 1,
                                    (short) 1))));
            pump(controller, erased::isDone);
            erased.join();
        }
    }
}
