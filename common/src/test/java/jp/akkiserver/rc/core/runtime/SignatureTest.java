package jp.akkiserver.rc.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureTest {

    @Test
    void parsesArgumentsAndReturn() {
        Signature s = Signature.parse("u16 u16 string u16 -> void");
        assertEquals(List.of(
            WireType.parse("u16"), WireType.parse("u16"),
            WireType.parse("string"), WireType.parse("u16")), s.params());
        assertEquals(WireType.VOID, s.ret());
    }

    @Test
    void parsesNoArguments() {
        Signature s = Signature.parse("-> u16");
        assertTrue(s.params().isEmpty());
        assertEquals(WireType.parse("u16"), s.ret());
    }

    @Test
    void parsesArrays() {
        Signature s = Signature.parse("u32 u8[] -> i32");
        assertEquals(WireType.parse("u8[]"), s.params().get(1));
    }

    @Test
    void arrowSpacingIsOptional() {
        assertEquals(Signature.parse("u16 -> i32"), Signature.parse("u16->i32"));
        assertEquals(Signature.parse("u16 -> i32"), Signature.parse("  u16   ->   i32  "));
    }

    @Test
    void formatsBackToTheSameText() {
        for (String text : new String[]{
            "u16 u16 string u16 -> void", "-> u16", "-> void", "u32 u8[] -> i32"}) {
            assertEquals(text, Signature.parse(text).toString());
        }
    }

    @Test
    void rejectsMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("u16 u16"));
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("u16 -> "));
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("u16 -> i32 i32"));
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("u16 -> i32 -> i32"));
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("void -> i32"));
        assertThrows(IllegalArgumentException.class, () -> Signature.parse("bytes -> i32"));
    }

    @Test
    void limitsArgumentCount() {
        List<WireType> max = Collections.nCopies(Signature.MAX_PARAMS, WireType.parse("u8"));
        assertEquals(Signature.MAX_PARAMS, new Signature(max, WireType.VOID).params().size());
        List<WireType> over = Collections.nCopies(Signature.MAX_PARAMS + 1, WireType.parse("u8"));
        assertThrows(IllegalArgumentException.class, () -> new Signature(over, WireType.VOID));
    }
}
