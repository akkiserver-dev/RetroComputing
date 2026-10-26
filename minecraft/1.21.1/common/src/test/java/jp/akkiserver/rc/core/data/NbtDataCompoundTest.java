package jp.akkiserver.rc.core.data;

import jp.akkiserver.rc.api.data.DataCompound;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtDataCompoundTest {
    private void fill(DataCompound data) {
        data.putInt("int", 42);
        data.putShort("short", (short) -2);
        data.putByte("byte", (byte) -1);
        data.putLong("long", -7);
        data.putFloat("float", 1.5f);
        data.putDouble("double", 3.25);
        data.putBoolean("bool", true);
        data.putString("text", "日本語");
        data.putUUID("uuid", new UUID(1, 2));
        data.putByteArray("bytes", new byte[]{-1, 2});
        data.putIntArray("ints", new int[]{1, 2});
        data.putLongArray("longs", new long[]{3});
        data.putShortArray("shorts", new short[]{-4});
        data.putFloatArray("floats", new float[]{1.5f});
        data.putDoubleArray("doubles", new double[]{-2.5});
        data.putBooleanArray("bools", new boolean[]{true, false});
        data.putStringArray("strings", new String[]{"あ", ""});
        data.putUUIDArray("uuids", new UUID[]{new UUID(1, 2)});
        MapDataCompound child = new MapDataCompound();
        child.putString("child", "nested");
        data.putCompound("nested", child);
        data.putCompoundArray("children", new DataCompound[]{child});
        data.putShortArray("emptyShorts", new short[0]);
        data.putFloatArray("emptyFloats", new float[0]);
        data.putDoubleArray("emptyDoubles", new double[0]);
        data.putBooleanArray("emptyBools", new boolean[0]);
        data.putStringArray("emptyStrings", new String[0]);
        data.putUUIDArray("emptyUUIDs", new UUID[0]);
        data.putCompoundArray("emptyCompounds", new DataCompound[0]);
    }

    private void check(DataCompound data) {
        assertEquals(42, data.getInt("int"));
        assertEquals(-2, data.getShort("short"));
        assertEquals(-1, data.getByte("byte"));
        assertEquals(-7, data.getLong("long"));
        assertEquals(1.5f, data.getFloat("float"));
        assertEquals(3.25, data.getDouble("double"));
        assertTrue(data.getBoolean("bool"));
        assertEquals("日本語", data.getString("text"));
        assertEquals(new UUID(1, 2), data.getUUID("uuid"));
        assertArrayEquals(new byte[]{-1, 2}, data.getByteArray("bytes"));
        assertArrayEquals(new int[]{1, 2}, data.getIntArray("ints"));
        assertArrayEquals(new long[]{3}, data.getLongArray("longs"));
        assertArrayEquals(new short[]{-4}, data.getShortArray("shorts"));
        assertArrayEquals(new float[]{1.5f}, data.getFloatArray("floats"));
        assertArrayEquals(new double[]{-2.5}, data.getDoubleArray("doubles"));
        assertArrayEquals(new boolean[]{true, false}, data.getBooleanArray("bools"));
        assertArrayEquals(new String[]{"あ", ""}, data.getStringArray("strings"));
        assertArrayEquals(new UUID[]{new UUID(1, 2)}, data.getUUIDArray("uuids"));
        assertEquals("nested", data.getCompound("nested").getString("child"));
        assertEquals("nested", data.getCompoundArray("children")[0].getString("child"));
        assertArrayEquals(new short[0], data.getShortArray("emptyShorts"));
        assertArrayEquals(new float[0], data.getFloatArray("emptyFloats"));
        assertArrayEquals(new double[0], data.getDoubleArray("emptyDoubles"));
        assertArrayEquals(new boolean[0], data.getBooleanArray("emptyBools"));
        assertArrayEquals(new String[0], data.getStringArray("emptyStrings"));
        assertArrayEquals(new UUID[0], data.getUUIDArray("emptyUUIDs"));
        assertEquals(0, data.getCompoundArray("emptyCompounds").length);
        assertNull(data.getString("missing"));
        assertNull(data.getString("int"));
        assertEquals(0, data.getLong("int"));
        assertNull(data.getCompound("text"));
    }

    @Test
    void bothImplementationsReadEachOthersStandardCompressedNbt() throws Exception {
        for (DataCompound data : List.of(new NbtDataCompound(), new MapDataCompound())) {
            fill(data);
            check(data);
            byte[] bytes = data.write();
            check(NbtIO.read(bytes));
            check(
                new NbtDataCompound(
                    NbtIo.readCompressed(
                        new ByteArrayInputStream(bytes),
                        NbtAccounter.create(16L << 20))));
        }
    }

    @Test
    void arraysAndCompoundsAreCopiedAndCallerStreamsStayOpen() throws Exception {
        NbtDataCompound data = new NbtDataCompound();
        byte[] bytes = {1};
        data.putByteArray("bytes", bytes);
        bytes[0] = 9;
        assertEquals(1, data.getByteArray("bytes")[0]);
        byte[] result = data.getByteArray("bytes");
        result[0] = 8;
        assertEquals(1, data.getByteArray("bytes")[0]);
        NbtDataCompound child = new NbtDataCompound();
        child.putInt("n", 1);
        data.putCompound("child", child);
        child.putInt("n", 2);
        assertEquals(1, data.getCompound("child").getInt("n"));
        class Stream extends ByteArrayOutputStream {
            boolean closed;

            public void close() {
                closed = true;
            }
        }
        Stream stream = new Stream();
        data.write(stream);
        assertFalse(stream.closed);
        assertNotNull(NbtIO.read(stream.toByteArray()));
    }
}
