package jp.akkiserver.rc.core.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtIOTest {
    @Test
    void roundTripsEverySupportedValueThroughStandardGzipNbt() throws IOException {
        var data = new MapDataCompound();
        UUID id = UUID.randomUUID();
        data.putInt("int", 42);
        data.putLong("long", Long.MIN_VALUE);
        data.putShort("short", (short) -2);
        data.putByte("byte", (byte) 3);
        data.putFloat("float", 1.25f);
        data.putDouble("double", -3.5);
        data.putBoolean("boolean", true);
        data.putString("string", "nul\0\ud83d\ude80");
        data.putUUID("uuid", id);
        data.putByteArray("bytes", new byte[]{-1, 2});
        data.putIntArray("ints", new int[]{1, -2});
        data.putLongArray("longs", new long[]{Long.MAX_VALUE});
        data.putShortArray("shorts", new short[]{-2, 5});
        data.putFloatArray("floats", new float[]{1.25f});
        data.putDoubleArray("doubles", new double[]{-3.5});
        data.putBooleanArray("booleans", new boolean[]{false, true});
        data.putStringArray("strings", new String[]{"", "ok"});
        data.putUUIDArray("uuids", new UUID[]{id});
        var nested = new MapDataCompound();
        nested.putString("name", "child");
        data.putCompound("compound", nested);
        data.putCompoundArray("compounds", new MapDataCompound[]{nested});
        MapDataCompound copy = NbtIO.read(data.write());
        assertEquals(data.keys(), copy.keys());
        assertEquals(42, copy.getInt("int"));
        assertEquals(Long.MIN_VALUE, copy.getLong("long"));
        assertEquals(-2, copy.getShort("short"));
        assertEquals(3, copy.getByte("byte"));
        assertEquals(1.25f, copy.getFloat("float"));
        assertEquals(-3.5, copy.getDouble("double"));
        assertTrue(copy.getBoolean("boolean"));
        assertEquals("nul\0\ud83d\ude80", copy.getString("string"));
        assertEquals(id, copy.getUUID("uuid"));
        assertArrayEquals(new byte[]{-1, 2}, copy.getByteArray("bytes"));
        assertArrayEquals(new int[]{1, -2}, copy.getIntArray("ints"));
        assertArrayEquals(new long[]{Long.MAX_VALUE}, copy.getLongArray("longs"));
        assertArrayEquals(new short[]{-2, 5}, copy.getShortArray("shorts"));
        assertArrayEquals(new float[]{1.25f}, copy.getFloatArray("floats"));
        assertArrayEquals(new double[]{-3.5}, copy.getDoubleArray("doubles"));
        assertArrayEquals(new boolean[]{false, true}, copy.getBooleanArray("booleans"));
        assertArrayEquals(new String[]{"", "ok"}, copy.getStringArray("strings"));
        assertArrayEquals(new UUID[]{id}, copy.getUUIDArray("uuids"));
        assertEquals("child", copy.getCompound("compound").getString("name"));
        assertEquals("child", copy.getCompoundArray("compounds")[0].getString("name"));
        try (var input =
                 new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data.write())))) {
            assertEquals(10, input.readUnsignedByte());
            assertEquals("", input.readUTF());
        }
    }

    @Test
    void preservesEmptyListElementTypes() throws IOException {
        var data = new MapDataCompound();
        data.putShortArray("shorts", new short[0]);
        data.putBooleanArray("bools", new boolean[0]);
        data.putStringArray("strings", new String[0]);
        data.putUUIDArray("ids", new UUID[0]);
        var copy = NbtIO.read(data.write());
        assertArrayEquals(new short[0], copy.getShortArray("shorts"));
        assertArrayEquals(new boolean[0], copy.getBooleanArray("bools"));
        assertArrayEquals(new String[0], copy.getStringArray("strings"));
        assertArrayEquals(new UUID[0], copy.getUUIDArray("ids"));
        assertNull(copy.getFloatArray("shorts"));
    }

    @Test
    void missingAndWrongTypesReturnDefaults() {
        var data = new MapDataCompound();
        data.putInt("n", 1);
        assertEquals(0, data.getInt("missing"));
        assertFalse(data.getBoolean("missing"));
        assertNull(data.getString("missing"));
        assertNull(data.getByteArray("missing"));
        assertNull(data.getString("n"));
        assertEquals(0, data.getLong("n"));
    }

    @Test
    void copiedArraysAndNestedCompoundsDoNotAlias() {
        var data = new MapDataCompound();
        int[] input = {1};
        data.putIntArray("array", input);
        input[0] = 2;
        data.getIntArray("array")[0] = 3;
        assertEquals(1, data.getIntArray("array")[0]);
        var child = new MapDataCompound();
        child.putInt("n", 1);
        data.putCompound("child", child);
        child.putInt("n", 2);
        data.getCompound("child").putInt("n", 3);
        assertEquals(1, data.getCompound("child").getInt("n"));
    }

    @Test
    void rejectsMalformedAndOversizedNbt() throws IOException {
        assertThrows(
            IOException.class,
            () -> NbtIO.read(new byte[]{10, 0, 0, 7, 0, 1, 97, -1, -1, -1, -1, 0}));
        assertThrows(IOException.class, () -> NbtIO.read(new byte[]{10, 0, 0, 0, 1}));
        var data = new MapDataCompound();
        data.putByteArray("large", new byte[1024]);
        assertThrows(
            IOException.class, () -> NbtIO.read(new ByteArrayInputStream(data.write()), 32));
    }
}
