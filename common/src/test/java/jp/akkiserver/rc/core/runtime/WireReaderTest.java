package jp.akkiserver.rc.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireReaderTest {
    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
    }

    @Test
    void readsMixedValuesSequentiallyUsingTheAbiByteLayout() {
        WireReader reader = new WireReader(hex("""
            01 ff 34 12 78 56 34 12 ff ff ff ff
            08 07 06 05 04 03 02 01 42 30
            00 00 c0 3f 00 00 00 00 00 00 04 c0
            06 00 00 00 e6 97 a5 e6 9c ac
            03 00 00 00 00 80 ff 00
            """));

        assertTrue(reader.readBoolean());
        assertEquals((byte) -1, reader.readByte());
        assertEquals((short) 0x1234, reader.readShort());
        assertEquals(0x12345678, reader.readInt());
        assertEquals(0xffff_ffffL, reader.readUInt());
        assertEquals(0x0102_0304_0506_0708L, reader.readLong());
        assertEquals('\u3042', reader.readChar());
        assertEquals(1.5f, reader.readFloat());
        assertEquals(-2.5, reader.readDouble());
        assertEquals("\u65e5\u672c", reader.readString());
        assertArrayEquals(hex("00 80 ff"), reader.readByteArray());
        assertFalse(reader.readBoolean());
    }

    @Test
    void emptyStringsAndArraysConsumeTheirLengthPrefixes() {
        WireReader reader = new WireReader(hex("""
            00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 01
            """));

        assertEquals("", reader.readString());
        assertArrayEquals(new byte[0], reader.readByteArray());
        assertArrayEquals(new short[0], reader.readShortArray());
        assertArrayEquals(new int[0], reader.readIntArray());
        assertArrayEquals(new long[0], reader.readLongArray());
        assertArrayEquals(new char[0], reader.readCharArray());
        assertArrayEquals(new float[0], reader.readFloatArray());
        assertArrayEquals(new double[0], reader.readDoubleArray());
        assertArrayEquals(new boolean[0], reader.readBooleanArray());
        assertArrayEquals(new String[0], reader.readStringArray());
        assertTrue(reader.readBoolean());
        reader.requireEnd();
    }

    @Test
    void readsStringArrayLengthsAsElementCountsAndPreservesTheFollowingValue() {
        WireReader reader = new WireReader(hex("""
            03 00 00 00
            00 00 00 00
            01 00 00 00 41
            06 00 00 00 e6 97 a5 e6 9c ac
            01
            """));

        assertArrayEquals(new String[]{"", "A", "\u65e5\u672c"}, reader.readStringArray());
        assertTrue(reader.readBoolean());
        reader.requireEnd();
    }

    @Test
    void rejectsInvalidStringArrayCountsAndMalformedLaterElements() {
        for (String payload : List.of(
            "",
            "01 00 00 00",
            "02 00 00 00 00 00 00 00",
            "00 00 00 40",
            "ff ff ff ff",
            "02 00 00 00 01 00 00 00 41 01 00 00 00",
            "02 00 00 00 01 00 00 00 41 01 00 00 00 80"
        )) {
            WireReader reader = new WireReader(hex(payload));
            assertThrows(IllegalArgumentException.class, reader::readStringArray);
        }
    }

    @Test
    void acceptsEndOfEmptyAndFullyReadInputs() {
        WireReader empty = new WireReader(new byte[0]);
        assertDoesNotThrow(empty::requireEnd);

        WireReader reader = new WireReader(hex("78 56 34 12"));
        assertThrows(IllegalArgumentException.class, reader::requireEnd);
        assertEquals(0x12345678, reader.readInt());
        assertDoesNotThrow(reader::requireEnd);
        assertDoesNotThrow(reader::requireEnd);
    }

    @Test
    void rejectsTrailingBytesWithoutConsumingThem() {
        WireReader reader = new WireReader(hex("01 00 00 00 7f 80"));
        assertEquals(1, reader.readInt());

        assertThrows(IllegalArgumentException.class, reader::requireEnd);
        assertEquals((byte) 0x7f, reader.readByte());
        assertEquals((byte) 0x80, reader.readByte());
        assertDoesNotThrow(reader::requireEnd);
    }

    @Test
    void readsFixedWidthArraysSequentiallyUsingTheAbiByteLayout() {
        WireReader reader = new WireReader(hex("""
            02 00 00 00
            08 07 06 05 04 03 02 01 00 00 00 00 00 00 00 80
            02 00 00 00 42 30 ff ff
            02 00 00 00 00 00 c0 3f 00 00 20 c0
            02 00 00 00
            00 00 00 00 00 00 f8 3f 00 00 00 00 00 00 04 c0
            03 00 00 00 01 00 01
            78 56 34 12
            """));

        assertArrayEquals(new long[]{0x0102_0304_0506_0708L, Long.MIN_VALUE}, reader.readLongArray());
        assertArrayEquals(new char[]{'\u3042', '\uffff'}, reader.readCharArray());
        assertArrayEquals(new float[]{1.5f, -2.5f}, reader.readFloatArray());
        assertArrayEquals(new double[]{1.5, -2.5}, reader.readDoubleArray());
        assertArrayEquals(new boolean[]{true, false, true}, reader.readBooleanArray());
        assertEquals(0x12345678, reader.readInt());
    }

    @Test
    void readsShortArrayElementsAndTheFollowingValue() {
        WireReader reader = new WireReader(hex("03 00 00 00 34 12 00 80 ff ff 01"));

        assertArrayEquals(new short[]{0x1234, Short.MIN_VALUE, -1}, reader.readShortArray());
        assertTrue(reader.readBoolean());
    }

    @Test
    void readsIntArrayElementsAndTheFollowingValue() {
        WireReader reader = new WireReader(hex("02 00 00 00 78 56 34 12 ff ff ff ff 01"));

        assertArrayEquals(new int[]{0x12345678, -1}, reader.readIntArray());
        assertTrue(reader.readBoolean());
    }

    @Test
    void rejectsArraysWhoseByteSizeExceedsTheInput() {
        for (String payload : List.of(
            "02 00 00 00 aa bb cc",
            "00 00 00 40",
            "ff ff ff ff"
        )) {
            byte[] data = hex(payload);
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readShortArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readIntArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readLongArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readCharArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readFloatArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readDoubleArray());
        }
    }

    @Test
    void rejectsIncompleteBooleanArraysAndInvalidElements() {
        for (String payload : List.of(
            "03 00 00 00 00 01",
            "03 00 00 00 00 01 02",
            "ff ff ff ff"
        )) {
            WireReader reader = new WireReader(hex(payload));
            assertThrows(IllegalArgumentException.class, reader::readBooleanArray);
        }
    }

    @Test
    void rejectsBooleanValuesOtherThanZeroAndOne() {
        for (String payload : List.of("02", "ff")) {
            WireReader reader = new WireReader(hex(payload));
            assertThrows(IllegalArgumentException.class, reader::readBoolean);
        }
    }

    @Test
    void truncatedScalarsReportInvalidArguments() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[0])::readBoolean),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[0])::readByte),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[1])::readShort),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[3])::readInt),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[3])::readUInt),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[7])::readLong),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[1])::readChar),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[3])::readFloat),
            () -> assertThrows(IllegalArgumentException.class,
                new WireReader(new byte[7])::readDouble)
        );
    }

    @Test
    void rejectsIncompleteAndOversizedLengthPrefixedValues() {
        for (String payload : List.of(
            "", "01", "01 00", "01 00 00",
            "03 00 00 00 aa bb",
            "ff ff ff 7f", "00 00 00 80", "ff ff ff ff"
        )) {
            byte[] data = hex(payload);
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readByteArray());
            assertThrows(IllegalArgumentException.class,
                () -> new WireReader(data).readString());
        }
    }

    @Test
    void rejectsMalformedUtf8WithoutReplacingCharacters() {
        for (String payload : List.of(
            "01 00 00 00 80",
            "02 00 00 00 c0 af",
            "02 00 00 00 e3 81",
            "03 00 00 00 ed a0 80",
            "04 00 00 00 f4 90 80 80"
        )) {
            WireReader reader = new WireReader(hex(payload));
            assertThrows(IllegalArgumentException.class, reader::readString);
        }
    }
}
