package jp.akkiserver.rc.core.storage;

import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.core.data.NbtIO;
import jp.akkiserver.rc.core.machine.MachineSnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Atomic machine generation pointer plus replayable per-device journals.
 */
public final class MachineStore {
    private final Path root, machine;
    private final Map<UUID, DeviceStorage> devices = new LinkedHashMap<>();
    private final boolean journaled;
    private Location location;

    public MachineStore(Path root, UUID id) throws IOException {
        this(root, id, true);
    }

    public MachineStore(Path root, UUID id, boolean journaled) throws IOException {
        this.journaled = journaled;
        this.root = root.toAbsolutePath().normalize();
        machine = this.root.resolve("machines").resolve(id.toString());
        Files.createDirectories(machine.resolve("generations"));
        recover();
    }

    private static void durable(Path path, byte[] bytes) throws IOException {
        try (FileChannel file =
                 FileChannel.open(
                     path,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) file.write(data);
            file.force(true);
        }
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '\"') out.append('\\').append(c);
            else if (c < 32 || c == 127)
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    public synchronized void location(Location location) {
        this.location = Objects.requireNonNull(location);
    }

    public synchronized DeviceStorage storage(UUID id) {
        return devices.computeIfAbsent(
            id,
            key -> {
                try {
                    Path path = root.resolve("devices").resolve(key.toString());
                    return journaled ? new JournaledStorage(path) : direct(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }

    private DeviceStorage direct(Path path) throws IOException {
        DirectStorage storage = new DirectStorage(path);
        return new DeviceStorage() {
            public SeekableByteChannel open(String name, OpenOption... options) throws IOException {
                for (OpenOption option : options)
                    if (option == StandardOpenOption.WRITE
                        || option == StandardOpenOption.APPEND
                        || option == StandardOpenOption.TRUNCATE_EXISTING) {
                        markDirectDirty();
                        break;
                    }
                return storage.open(name, options);
            }

            public boolean exists(String name) {
                return storage.exists(name);
            }

            public void delete(String name) throws IOException {
                markDirectDirty();
                storage.delete(name);
            }
        };
    }

    private synchronized void markDirectDirty() throws IOException {
        Path marker = machine.resolve("DIRECT_DIRTY");
        if (!Files.exists(marker)) durable(marker, new byte[]{1});
    }

    private Path generation() throws IOException {
        Path current = machine.resolve("CURRENT");
        if (!Files.exists(current)) return null;
        String name = Files.readString(current, StandardCharsets.US_ASCII).trim();
        try {
            UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid generation marker", e);
        }
        return machine.resolve("generations").resolve(name);
    }

    /**
     * Must run while this machine is paused. Disk and runtime data share one commit point.
     */
    public synchronized void save(MachineSnapshot snapshot) throws IOException {
        recover();
        Path generation = machine.resolve("generations").resolve(UUID.randomUUID().toString());
        Files.createDirectory(generation);
        Path journals = generation.resolve("devices");
        Files.createDirectory(journals);
        for (var entry : devices.entrySet())
            if (entry.getValue() instanceof JournaledStorage overlay)
                durable(journals.resolve(entry.getKey() + ".nbt"), overlay.journal().write());
        durable(generation.resolve("machine.nbt"), snapshot.write());
        Path marker = machine.resolve("CURRENT.next");
        durable(marker, generation.getFileName().toString().getBytes(StandardCharsets.US_ASCII));
        Files.move(
            marker,
            machine.resolve("CURRENT"),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        recover();
        for (DeviceStorage storage : devices.values())
            if (storage instanceof JournaledStorage overlay) overlay.committed();
        Files.deleteIfExists(machine.resolve("DIRECT_DIRTY"));
        metadata(machine, "machine");
        for (UUID id : devices.keySet())
            metadata(root.resolve("devices").resolve(id.toString()), "device");
    }

    public synchronized MachineSnapshot load() throws IOException {
        recover();
        if (Files.exists(machine.resolve("DIRECT_DIRTY"))) return null;
        Path generation = generation();
        return generation == null
            ? null
            : MachineSnapshot.read(Files.readAllBytes(generation.resolve("machine.nbt")));
    }

    /**
     * Replay CURRENT only. Prepared generations without a committed marker are ignored.
     */
    public synchronized void recover() throws IOException {
        Path generation = generation();
        if (generation == null || Files.exists(generation.resolve("APPLIED"))) return;
        if (!Files.isRegularFile(generation.resolve("machine.nbt")))
            throw new IOException("Committed machine snapshot missing");
        try (var paths = Files.list(generation.resolve("devices"))) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".nbt")) throw new IOException("Invalid device journal name");
                UUID id;
                try {
                    id = UUID.fromString(name.substring(0, name.length() - 4));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid device id", e);
                }
                DeviceStorage existing = storage(id);
                JournaledStorage storage =
                    existing instanceof JournaledStorage overlay
                        ? overlay
                        : new JournaledStorage(
                        root.resolve("devices").resolve(id.toString()));
                storage.replay(NbtIO.read(Files.readAllBytes(path)));
            }
        }
        durable(generation.resolve("APPLIED"), new byte[]{1});
    }

    private void metadata(Path folder, String kind) throws IOException {
        if (location == null) return;
        String text =
            "world = "
                + quote(location.world())
                + "\ndimension = "
                + quote(location.dimension())
                + "\nlast_access = "
                + Instant.now()
                + "\nkind = \""
                + kind
                + "\"\npos = ["
                + location.x()
                + ", "
                + location.y()
                + ", "
                + location.z()
                + "]\n";
        Path next = folder.resolve("meta.toml.next");
        durable(next, text.getBytes(StandardCharsets.UTF_8));
        Files.move(
            next,
            folder.resolve("meta.toml"),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
    }

    public record Location(String world, String dimension, int x, int y, int z) {
        public Location {
            Objects.requireNonNull(world);
            Objects.requireNonNull(dimension);
        }
    }
}
