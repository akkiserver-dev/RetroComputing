package jp.akkiserver.rc.emulator;

import jp.akkiserver.rc.api.video.CardSpec;
import jp.akkiserver.rc.api.video.Monitor;
import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.api.video.Size;
import jp.akkiserver.rc.api.video.VideoMode;
import jp.akkiserver.rc.architecture.shuttle.ShuttleArchitectureFactory;
import jp.akkiserver.rc.core.device.DiskDevice;
import jp.akkiserver.rc.core.device.EepromDevice;
import jp.akkiserver.rc.core.device.KeyboardDevice;
import jp.akkiserver.rc.core.device.VideoDevice;
import jp.akkiserver.rc.core.machine.Machine;
import jp.akkiserver.rc.core.machine.MachineConfig;
import jp.akkiserver.rc.core.machine.MachineWorkers;
import jp.akkiserver.rc.core.runtime.MethodCodec;
import jp.akkiserver.rc.core.runtime.MethodTable;
import jp.akkiserver.rc.core.storage.MemoryStorage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The caller owns tick; the Swing event thread only submits requests and receives frames.
 */
final class EmulatorController implements AutoCloseable {
    final ShuttleArchitectureFactory factory = new ShuttleArchitectureFactory();
    final ExecutorService workers = MachineWorkers.create(1, 8L << 20);
    final VideoDevice video;
    final KeyboardDevice keyboard = new KeyboardDevice(UUID.randomUUID());
    final Machine machine;
    final DisplayBuffer display = new DisplayBuffer();
    final ConcurrentLinkedQueue<String> logs = new ConcurrentLinkedQueue<>();
    private final AtomicReference<VideoDevice.DisplayState> capture = new AtomicReference<>();
    private CompletableFuture<Void> captureRequest = CompletableFuture.completedFuture(null);
    private volatile boolean closing;
    private CompletableFuture<Void> shutdown;
    private boolean closed;
    private volatile byte[] image;

    EmulatorController(Path boot) throws Exception {
        video =
            new VideoDevice(
                UUID.randomUUID(),
                new CardSpec(
                    1 << 20,
                    65536,
                    Set.of(PixelFormat.values()),
                    true,
                    List.of(
                        new VideoMode(0, PixelFormat.TEXT16, 80, 25),
                        new VideoMode(1, PixelFormat.TEXT256, 80, 25),
                        new VideoMode(2, PixelFormat.IDX1, 320, 200),
                        new VideoMode(3, PixelFormat.IDX2, 320, 200),
                        new VideoMode(4, PixelFormat.IDX4, 320, 200),
                        new VideoMode(16, PixelFormat.IDX8, 320, 200),
                        new VideoMode(17, PixelFormat.IDX8, 640, 480),
                        new VideoMode(32, PixelFormat.RGB565, 320, 200),
                        new VideoMode(33, PixelFormat.RGB565, 640, 480))));
        video.connect(Monitor.multisync(false, new Size(160, 60), new Size(1024, 768)));
        int[] colors = {
            0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xaa5500, 0xaaaaaa,
            0x555555, 0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff
        };
        for (int i = 0; i < colors.length; i++)
            video.setPalette((byte) i, colors[i]);

        Map<UUID, MemoryStorage> storage = new HashMap<>();
        machine =
            new Machine(
                MachineConfig.defaults(),
                factory,
                workers,
                id -> storage.computeIfAbsent(id, k -> new MemoryStorage()),
                logs::add);
        machine.requestAttach(0, new EepromDevice(UUID.randomUUID(), new byte[0]));
        machine.requestAttach(1, video);
        machine.requestAttach(2, keyboard);
        machine.requestAttach(3, new DiskDevice(UUID.randomUUID(), 16384, false));

        image = boot == null ? demo() : read(boot);
        report(machine.requestPowerOn(image));
    }

    private static byte[] read(Path path) throws Exception {
        if (Files.size(path) > 64L << 20)
            throw new IllegalArgumentException("Wasm exceeds 64 MiB");
        return Files.readAllBytes(path);
    }

    private void report(CompletableFuture<?> future) {
        future.whenComplete(
            (v, e) -> {
                if (e != null)
                    logs.add("ERROR: " + e);
            });
    }

    synchronized void reload(Path path) {
        if (closing)
            return;

        byte[] replacement;
        try {
            replacement = path == null ? image : read(path);
        } catch (Exception e) {
            logs.add("ERROR: " + e);
            return;
        }

        byte[] next = replacement;
        report(
            machine.requestPowerOff()
                .thenCompose(
                    v -> {
                        synchronized (this) {
                            if (closing)
                                return CompletableFuture.completedFuture(null);
                            image = next;
                            return machine.requestPowerOn(next);
                        }
                    }));
    }

    void stop() {
        report(machine.requestPowerOff());
    }

    synchronized void requestClose() {
        closing = true;
    }

    /**
     * Returns false once native resources have been released at a safe boundary.
     */
    boolean tick(Consumer<BufferedImage> frames) {
        if (closed)
            return false;
        if (closing && shutdown == null)
            shutdown = machine.requestPowerOff();
        if (!closing && captureRequest.isDone())
            captureRequest = machine.requestHostAction(() -> capture.set(video.captureDisplay()));

        machine.tick();
        VideoDevice.DisplayState state = capture.getAndSet(null);
        if (state != null) {
            display.accept(state);
            frames.accept(display.render());
        }

        if (shutdown != null && shutdown.isDone()) {
            shutdown.join();
            try {
                factory.close();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot close runtime", e);
            } finally {
                workers.shutdown();
                closed = true;
            }
            return false;
        }
        return true;
    }

    String status() {
        return machine.state()
            + "  |  tick "
            + machine.time()
            + "  |  video:1 keyboard:2 disk:3 (64 MiB, volatile)"
            + (machine.failure() == null ? "" : "  |  " + machine.failure());
    }

    @Override
    public void close() {
        requestClose();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!closed && System.nanoTime() < deadline) {
            tick(f -> {
            });
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (!closed)
            throw new IllegalStateException("Guest did not stop");
    }

    private byte[] demo() {
        MethodTable table = MethodTable.create(video);
        int write =
            table.methods().stream()
                .filter(m -> m.name().equals("write"))
                .findFirst()
                .orElseThrow()
                .id();
        DemoWasm wasm =
            new DemoWasm()
                .imp("fw", "dev_call", "iiiiii>i")
                .imp("fw", "hlt", "l>i")
                .imp("fw", "poll_event", "ii>i");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        String[] lines = {
            "Retro Computings",
            "Standalone display / real Shuttle guest",
            "",
            "Open a .wasm file to boot your Rust OS.",
            "Reload rereads the file after rebuilding.",
            "",
            "Video slot 1 / Keyboard slot 2 / Disk slot 3",
            "Default disk is volatile. No Minecraft required.",
            "",
            "See guests/examples/screen for a no_std Rust starter."
        };

        for (int y = 0; y < lines.length; y++) {
            byte[] args =
                MethodCodec.encodeArguments(
                    table.javaMethod(write).getParameterTypes(),
                    new Object[]{
                        (short) 3, (short) (y + 2), lines[y], (short) (y == 0 ? 11 : 15)
                    });
            wasm.i(1).i(write).i(data.size()).i(args.length).i(0).i(0).call(0).op(0x1a);
            data.writeBytes(args);
        }

        wasm.op(0x03, 0x40).i(8192).i(256).call(2).op(0x1a).l(1).call(1).op(0x1a, 0x0c, 0, 0x0b);
        wasm.data = data.toByteArray();
        return wasm.bytes();
    }
}
