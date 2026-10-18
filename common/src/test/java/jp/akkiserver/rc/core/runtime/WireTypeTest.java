package jp.akkiserver.rc.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WireTypeTest {

    @Test
    void codesMatchTheAbiTable() {
        assertEquals(0x00, WireType.VOID.code());
        assertEquals(0x01, WireType.parse("bool").code());
        assertEquals(0x02, WireType.parse("i8").code());
        assertEquals(0x03, WireType.parse("u8").code());
        assertEquals(0x05, WireType.parse("u16").code());
        assertEquals(0x07, WireType.parse("u32").code());
        assertEquals(0x09, WireType.parse("u64").code());
        assertEquals(0x0B, WireType.parse("f64").code());
        assertEquals(0x0C, WireType.parse("string").code());
    }

    @Test
    void arrayFlagIsBit7() {
        assertEquals(0x85, WireType.parse("u16[]").code());
        assertEquals(0x8C, WireType.parse("string[]").code());
        assertEquals(0x82, WireType.parse("i8[]").code());
    }

    @Test
    void codeRoundTrips() {
        for (int code : new int[] {0x00, 0x01, 0x0C, 0x81, 0x85, 0x8C}) {
            assertEquals(code, WireType.fromCode(code).code());
        }
    }

    @Test
    void textRoundTrips() {
        for (String text : new String[] {"void", "bool", "i32", "u64[]", "string", "string[]", "f32[]"}) {
            assertEquals(text, WireType.parse(text).toString());
        }
    }

    @Test
    void parseIgnoresSurroundingWhitespace() {
        assertEquals(WireType.arrayOf(WireType.Kind.U8), WireType.parse("  u8[] "));
    }

    @Test
    void rejectsUnknownAndBrokenTypes() {
        assertThrows(IllegalArgumentException.class, () -> WireType.parse("bytes"));
        assertThrows(IllegalArgumentException.class, () -> WireType.parse("int"));
        assertThrows(IllegalArgumentException.class, () -> WireType.parse(""));
        assertThrows(IllegalArgumentException.class, () -> WireType.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> WireType.parse("u16[][]"));
        assertThrows(IllegalArgumentException.class, () -> WireType.fromCode(0x0D));
        assertThrows(IllegalArgumentException.class, () -> WireType.fromCode(0x100));
        assertThrows(IllegalArgumentException.class, () -> WireType.fromCode(-1));
    }

    @Test
    void voidIsNeverAnArray() {
        assertThrows(IllegalArgumentException.class, () -> WireType.parse("void[]"));
        assertThrows(IllegalArgumentException.class, () -> WireType.fromCode(0x80));
    }
}
