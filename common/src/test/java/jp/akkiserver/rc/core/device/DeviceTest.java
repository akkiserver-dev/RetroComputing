package jp.akkiserver.rc.core.device;

import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.api.video.CardSpec;
import jp.akkiserver.rc.api.video.Monitor;
import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.api.video.Size;
import jp.akkiserver.rc.api.video.VideoMode;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.runtime.MethodTable;
import jp.akkiserver.rc.core.storage.MemoryStorage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceTest {
    @Test
    void fullwidthTextUsesTwoCellsAndModeIdsSurviveFiltering() {
        VideoDevice video =
            new VideoDevice(
                UUID.randomUUID(),
                new CardSpec(
                    65536,
                    100,
                    Set.of(PixelFormat.TEXT16),
                    true,
                    List.of(
                        new VideoMode(7, PixelFormat.TEXT16, 5, 2),
                        new VideoMode(42, PixelFormat.TEXT16, 10, 2))));
        video.connect(Monitor.multisync(false, new Size(5, 2), new Size(10, 10)));
        assertEquals(7, video.getMode());
        video.write((short) 0, (short) 0, "日A", (short) 7);
        byte[] bytes = video.read((short) 0, (short) 0, (short) 3, (short) 1);
        assertEquals('日', ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getChar());
        assertEquals(0, bytes[3]);
        assertEquals('A', bytes[6]);
        video.connect(Monitor.multisync(false, new Size(10, 2), new Size(10, 10)));
        video.setMode((short) 42);
        assertEquals(42, video.getMode());
        video.powerChanged(true);
        assertEquals(7, video.getMode());
    }

    @Test
    void removableMediaKeepsItsIdentityAndDataAcrossDrives() {
        Map<UUID, DeviceStorage> media = new HashMap<>();
        Function<UUID, DeviceStorage> storage =
            id -> media.computeIfAbsent(id, ignored -> new MemoryStorage());
        FloppyDevice first = new FloppyDevice(UUID.randomUUID(), storage),
            second = new FloppyDevice(UUID.randomUUID(), storage);
        first.attached(new Context());
        second.attached(new Context());
        UUID id = UUID.randomUUID();
        first.insert(id, 360, false);
        byte[] block = new byte[4096];
        block[0] = 9;
        first.write(0, block);
        assertEquals(id, first.eject());
        assertThrows(DeviceException.class, () -> first.read(0, (short) 1));
        second.insert(id, 360, true);
        assertArrayEquals(block, second.read(0, (short) 1));
        assertThrows(DeviceException.class, () -> second.write(0, block));
        assertEquals(
            7, ByteBuffer.wrap(second.getInfo()).order(ByteOrder.LITTLE_ENDIAN).getShort(8));
        assertNotNull(MethodTable.create(second));
    }

    @Test
    void eepromLockAndConfigurationSurviveSave() {
        EepromDevice device = new EepromDevice(UUID.randomUUID(), new byte[]{1});
        device.writeData(new byte[]{2, 3});
        device.writeData(new byte[]{4});
        assertEquals(256, device.readData().length);
        assertEquals(3, device.readData()[1]);
        device.lock();
        assertEquals(
            StdErrors.E_READONLY,
            assertThrows(DeviceException.class, () -> device.flash(new byte[]{3})).code());
        MapDataCompound data = new MapDataCompound();
        device.save(data);
        EepromDevice restored = new EepromDevice(device.info().address(), new byte[0]);
        restored.load(data);
        assertArrayEquals(device.readCode(), restored.readCode());
        assertArrayEquals(device.readData(), restored.readData());
        assertThrows(DeviceException.class, () -> restored.flash(new byte[0]));
        assertEquals(6, MethodTable.create(device).methods().size());
    }

    @Test
    void diskHasSparseZeroSectorsAndValidatesBounds() {
        DiskDevice disk = new DiskDevice(UUID.randomUUID(), 8, false);
        disk.attached(new Context());
        assertArrayEquals(new byte[4096], disk.read(0, (short) 1));
        byte[] block = new byte[4096];
        Arrays.fill(block, (byte) 8);
        disk.write(2, block);
        assertArrayEquals(block, disk.read(2, (short) 1));
        assertThrows(DeviceException.class, () -> disk.read(-1, (short) 1));
        assertThrows(DeviceException.class, () -> disk.write(0, new byte[4095]));
        assertEquals(2, ByteBuffer.wrap(disk.getInfo()).order(ByteOrder.LITTLE_ENDIAN).getShort(8));
        disk.detached();
        assertEquals(
            StdErrors.E_NOMEDIA,
            assertThrows(DeviceException.class, () -> disk.read(0, (short) 1)).code());
    }

    VideoDevice video(PixelFormat format, int width, int height, int bandwidth) {
        VideoDevice device =
            new VideoDevice(
                UUID.randomUUID(),
                new CardSpec(
                    65536,
                    bandwidth,
                    Set.of(format),
                    true,
                    List.of(new VideoMode(0, format, width, height))));
        device.connect(Monitor.multisync(160, 60, 1024, 768, false));
        return device;
    }

    @Test
    void textCopyHandlesOverlapAndClips() {
        VideoDevice video = video(PixelFormat.TEXT16, 5, 1, 5);
        video.write((short) 0, (short) 0, "ABCDE", (short) 7);
        video.copy((short) 0, (short) 0, (short) 5, (short) 1, (short) 1, (short) 0);
        byte[] result = video.read((short) 0, (short) 0, (short) 5, (short) 1);
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < 5; i++) chars.append((char) result[i * 3]);
        assertEquals("AABCD", chars.toString());
        assertEquals(5, video.drainUpdates().stream().mapToInt(u -> u.bytes().length).sum());
        assertEquals(5, video.drainUpdates().stream().mapToInt(u -> u.bytes().length).sum());
    }

    @Test
    void packedPixelsHonorRowAlignmentAndUnalignedRectangles() {
        for (PixelFormat format : List.of(PixelFormat.IDX1, PixelFormat.IDX2, PixelFormat.IDX4)) {
            VideoDevice video = video(format, 13, 2, 100);
            video.fill((short) 1, (short) 0, (short) 5, (short) 2, 1);
            byte[] packed = video.read((short) 1, (short) 0, (short) 5, (short) 2);
            VideoDevice other = video(format, 13, 2, 100);
            other.blit((short) 3, (short) 0, (short) 5, (short) 2, packed);
            assertArrayEquals(packed, other.read((short) 3, (short) 0, (short) 5, (short) 2));
            assertArrayEquals(
                new byte[format.rowBytes(1) * 2],
                other.read((short) 2, (short) 0, (short) 1, (short) 2));
        }
    }

    @Test
    void monitorFilteringAndVideoStateRoundTrip() {
        VideoDevice video = video(PixelFormat.TEXT256, 5, 2, 100);
        video.write((short) 0, (short) 0, "Hi", (short) 0x1234);
        video.setPalette((byte) 2, 0xabcdef);
        video.setCursor((short) 2, (short) 1, true);
        MapDataCompound data = new MapDataCompound();
        video.save(data);
        video.setMode((short) 0);
        video.load(data);
        assertEquals(0xabcdef, video.getPalette((byte) 2));
        assertTrue(video.cursorVisible());
        assertEquals(2, video.cursorX());
        assertEquals('H', video.read((short) 0, (short) 0, (short) 1, (short) 1)[0]);
        video.connect(null);
        assertEquals(0, video.modes().length);
        assertThrows(DeviceException.class, () -> video.setMode((short) 0));
        assertNotNull(MethodTable.create(video));
    }

    static class Context implements DeviceContext {
        final List<byte[]> signals = new ArrayList<>();
        final MemoryStorage storage = new MemoryStorage();

        public int slot() {
            return 0;
        }

        public void signal(byte[] bytes) {
            signals.add(bytes.clone());
        }

        public DeviceStorage storage() {
            return storage;
        }

        public boolean hasPower() {
            return true;
        }
    }
}
