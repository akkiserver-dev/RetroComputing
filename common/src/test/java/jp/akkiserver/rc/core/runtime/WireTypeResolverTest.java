package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.Unsigned;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WireTypeResolverTest {

    @SuppressWarnings("unused")
    static class Sample {
        void write(@Unsigned short x, @Unsigned short y, String s, @Unsigned short attr) {}
        @Unsigned short getMode() { return 0; }
        void place(int x, int y, int z) {}
        long mixed(boolean b, byte a, @Unsigned byte c, char d, float e, double f) { return 0; }
        byte[] signedBytes(byte[] in) { return in; }
        @Unsigned byte[] unsignedBytes(@Unsigned byte[] in) { return in; }
        String[] names(int[] a, @Unsigned int[] b, long[] c) { return null; }
        void unsignedBool(@Unsigned boolean b) {}
        void unsignedString(@Unsigned String s) {}
        void unsignedChar(@Unsigned char c) {}
        void unsignedChars(@Unsigned char[] chars) {}
        @Unsigned char[] unsignedCharResult() { return new char[0]; }
        @Unsigned void unsignedVoid() {}
        void boxed(Integer i) {}
        void nested(int[][] i) {}
        void object(Object o) {}
    }

    private static Method method(String name) {
        for (Method m : Sample.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("no method " + name);
    }

    private static Signature sig(String name) {
        return WireTypeResolver.forMethod(method(name));
    }

    @Test
    void resolvesSpecExamples() {
        assertEquals("u16 u16 string u16 -> void", sig("write").toString());
        assertEquals("-> u16", sig("getMode").toString());
        assertEquals("i32 i32 i32 -> void", sig("place").toString());
    }

    @Test
    void resolvesEveryPrimitive() {
        assertEquals("bool i8 u8 u16 f32 f64 -> i64", sig("mixed").toString());
    }

    @Test
    void byteArraysFollowTheSign() {
        assertEquals("i8[] -> i8[]", sig("signedBytes").toString());
        assertEquals("u8[] -> u8[]", sig("unsignedBytes").toString());
    }

    @Test
    void otherArrays() {
        assertEquals("i32[] u32[] i64[] -> string[]", sig("names").toString());
    }

    @Test
    void unsignedIsRejectedWhereItMeansNothing() {
        for (String name : List.of("unsignedBool", "unsignedString", "unsignedChar", "unsignedVoid")) {
            assertThrows(IllegalArgumentException.class, () -> sig(name), name);
        }
    }

    @Test
    void charArraysDoNotAcceptUnsignedAnnotations() {
        assertEquals(WireType.parse("u16[]"), WireTypeResolver.of(char[].class, false));
        assertThrows(IllegalArgumentException.class, () -> sig("unsignedChars"));
        assertThrows(IllegalArgumentException.class, () -> sig("unsignedCharResult"));
    }

    @Test
    void unsupportedTypesAreRejected() {
        for (String name : List.of("boxed", "nested", "object")) {
            assertThrows(IllegalArgumentException.class, () -> sig(name), name);
        }
    }

    @Test
    void resolvesFromClassDirectly() {
        assertEquals(WireType.parse("u64"), WireTypeResolver.of(long.class, true));
        assertEquals(WireType.parse("u16"), WireTypeResolver.of(char.class, false));
        assertEquals(WireType.VOID, WireTypeResolver.of(void.class, false));
    }
}
