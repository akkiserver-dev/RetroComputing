package jp.akkiserver.rc.core.storage;

import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.machine.MachineSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;

import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {
    @TempDir
    Path root;

    @Test
    void directWritesAfterASnapshotRequireColdBootAndMetadataIsToml() throws Exception {
        UUID machine = UUID.randomUUID(), device = UUID.randomUUID();
        MachineStore store = new MachineStore(root, machine, false);
        store.location(new MachineStore.Location("world\n日本語", "minecraft:overworld", 1, 64, -2));
        MapDataCompound data = new MapDataCompound();
        data.putInt("version", 1);
        data.putString("architecture", "test");
        data.putByteArray("boot", new byte[0]);
        data.putByteArray("runtime", new byte[0]);
        write(store.storage(device), "disk.bin", 0, new byte[]{1});
        store.save(new MachineSnapshot(data));
        assertNotNull(store.load());
        var meta =
            Toml.parse(
                root.resolve("machines").resolve(machine.toString()).resolve("meta.toml"));
        assertFalse(meta.hasErrors());
        assertEquals("world\n日本語", meta.getString("world"));
        write(store.storage(device), "disk.bin", 0, new byte[]{2});
        assertNull(new MachineStore(root, machine, false).load());
        store.save(new MachineSnapshot(data));
        assertNotNull(store.load());
    }

    void write(DeviceStorage storage, String name, int offset, byte[] bytes) throws Exception {
        try (var file = storage.open(name, CREATE, WRITE)) {
            file.position(offset);
            file.write(ByteBuffer.wrap(bytes));
        }
    }

    byte[] read(DeviceStorage storage, String name) throws Exception {
        try (var file = storage.open(name, READ)) {
            byte[] bytes = new byte[(int) file.size()];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) assertTrue(file.read(buffer) > 0);
            return bytes;
        }
    }

    @Test
    void journalWritesRemainInvisibleUntilReplayAndReplayIsIdempotent() throws Exception {
        JournaledStorage storage = new JournaledStorage(root);
        byte[] bytes = new byte[8194];
        new Random(42).nextBytes(bytes);
        write(storage, "disk.bin", 0, bytes);
        assertArrayEquals(bytes, read(storage, "disk.bin"));
        assertFalse(Files.exists(root.resolve("disk.bin")));
        var journal = storage.journal();
        storage.replay(journal);
        storage.replay(journal);
        storage.committed();
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("disk.bin")));
        write(storage, "disk.bin", 4095, new byte[]{1, 2, 3});
        bytes[4095] = 1;
        bytes[4096] = 2;
        bytes[4097] = 3;
        storage.replay(storage.journal());
        storage.committed();
        assertArrayEquals(bytes, read(storage, "disk.bin"));
    }

    @Test
    void truncateThenExtendDoesNotResurrectOldBytes() throws Exception {
        JournaledStorage storage = new JournaledStorage(root);
        byte[] bytes = new byte[9000];
        Arrays.fill(bytes, (byte) 7);
        write(storage, "disk.bin", 0, bytes);
        storage.replay(storage.journal());
        storage.committed();
        try (var file = storage.open("disk.bin", WRITE)) {
            file.truncate(4000);
            file.position(8500);
            file.write(ByteBuffer.wrap(new byte[]{9}));
        }
        byte[] expected = new byte[8501];
        Arrays.fill(expected, 0, 4000, (byte) 7);
        expected[8500] = 9;
        assertArrayEquals(expected, read(storage, "disk.bin"));
        storage.replay(storage.journal());
        storage.committed();
        assertArrayEquals(expected, Files.readAllBytes(root.resolve("disk.bin")));
    }

    @Test
    void crashRecoveryUsesCommittedGenerationOnly() throws Exception {
        UUID machineId = UUID.randomUUID(), deviceId = UUID.randomUUID();
        MachineStore store = new MachineStore(root, machineId);
        write(store.storage(deviceId), "disk.bin", 0, new byte[]{4, 5});
        MapDataCompound data = new MapDataCompound();
        data.putInt("version", 1);
        data.putString("architecture", "test");
        data.putByteArray("boot", new byte[]{1});
        data.putByteArray("runtime", new byte[]{2});
        store.save(new MachineSnapshot(data));
        Path machine = root.resolve("machines").resolve(machineId.toString()),
            generation =
                machine.resolve("generations")
                    .resolve(Files.readString(machine.resolve("CURRENT")));
        Files.delete(generation.resolve("APPLIED"));
        Files.write(
            root.resolve("devices").resolve(deviceId.toString()).resolve("disk.bin"),
            new byte[]{0});
        MachineStore recovered = new MachineStore(root, machineId, false);
        assertArrayEquals(new byte[]{4, 5}, read(recovered.storage(deviceId), "disk.bin"));
        assertNotNull(recovered.load());
        JournaledStorage pending =
            new JournaledStorage(root.resolve("devices").resolve(deviceId.toString()));
        write(pending, "disk.bin", 0, new byte[]{99});
        MachineStore abandoned = new MachineStore(root, machineId);
        assertArrayEquals(new byte[]{4, 5}, read(abandoned.storage(deviceId), "disk.bin"));
    }

    @Test
    void directMemoryAndJournalRespectBasicChannelContracts() throws Exception {
        for (DeviceStorage storage :
            List.of(
                new MemoryStorage(),
                new DirectStorage(root.resolve("direct")),
                new JournaledStorage(root.resolve("journal")))) {
            write(storage, "data.bin", 4, new byte[]{1, 2});
            assertArrayEquals(new byte[]{0, 0, 0, 0, 1, 2}, read(storage, "data.bin"));
            try (var channel = storage.open("data.bin", WRITE)) {
                channel.truncate(2);
                assertEquals(2, channel.size());
            }
            assertThrows(
                FileAlreadyExistsException.class,
                () -> storage.open("data.bin", CREATE_NEW, WRITE));
            assertThrows(Exception.class, () -> storage.open("../escape", CREATE, WRITE));
            storage.delete("data.bin");
            assertFalse(storage.exists("data.bin"));
        }
    }
}
