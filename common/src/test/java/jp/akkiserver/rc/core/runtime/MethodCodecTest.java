package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.Unsigned;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodCodecTest {
    @Module
    public static class ArrayModule {
        @Export(async = true)
        public @Unsigned byte[] transfer(@Unsigned short bank, byte[] signedData,
                                        @Unsigned byte[] unsignedData, String[] tags) {
            throw new AssertionError("Describing methods must not execute them");
        }
    }

    @Module
    public static class ArgumentModule {
        @Export
        public void scalars(boolean flag, byte b, short s, int i, long l,
                            char c, float f, double d, String text) {
            throw new AssertionError("Decoding arguments must not execute methods");
        }

        @Export
        public void arrays(boolean[] flags, byte[] bytes, short[] shorts, int[] ints,
                           long[] longs, char[] chars, float[] floats, double[] doubles,
                           String[] strings) {
            throw new AssertionError("Decoding arguments must not execute methods");
        }

        @Export
        public void unsigned(@Unsigned byte b, @Unsigned short s, char c,
                             @Unsigned int i, @Unsigned long l) {
            throw new AssertionError("Decoding arguments must not execute methods");
        }
    }

    @Test
    void decodesScalarsIntoTheirDeclaredJavaTypes() throws NoSuchMethodException {
        Method method = ArgumentModule.class.getMethod("scalars",
            boolean.class, byte.class, short.class, int.class, long.class,
            char.class, float.class, double.class, String.class);
        byte[] data = hex("""
            01 ff 34 12 78 56 34 12
            08 07 06 05 04 03 02 01 42 30
            00 00 c0 3f 00 00 00 00 00 00 04 c0
            06 00 00 00 e6 97 a5 e6 9c ac
            """);

        Object[] expected = {
            true, (byte) -1, (short) 0x1234, 0x12345678, 0x0102_0304_0506_0708L,
            '\u3042', 1.5f, -2.5, "\u65e5\u672c"
        };
        assertArrayEquals(expected, MethodCodec.decodeArguments(method, data));
    }

    @Test
    void decodesArraysIntoTheirDeclaredJavaTypes() throws NoSuchMethodException {
        Method method = ArgumentModule.class.getMethod("arrays",
            boolean[].class, byte[].class, short[].class, int[].class, long[].class,
            char[].class, float[].class, double[].class, String[].class);
        byte[] data = hex("""
            02 00 00 00 01 00
            02 00 00 00 00 ff
            02 00 00 00 34 12 ff ff
            02 00 00 00 78 56 34 12 ff ff ff ff
            01 00 00 00 08 07 06 05 04 03 02 01
            02 00 00 00 42 30 ff ff
            01 00 00 00 00 00 c0 3f
            01 00 00 00 00 00 00 00 00 00 04 c0
            02 00 00 00 00 00 00 00 06 00 00 00 e6 97 a5 e6 9c ac
            """);

        Object[] args = MethodCodec.decodeArguments(method, data);
        Object[] expected = {
            new boolean[] {true, false}, new byte[] {0, -1},
            new short[] {0x1234, -1}, new int[] {0x12345678, -1},
            new long[] {0x0102_0304_0506_0708L}, new char[] {'\u3042', '\uffff'},
            new float[] {1.5f}, new double[] {-2.5}, new String[] {"", "\u65e5\u672c"}
        };
        assertArrayEquals(expected, args);
        for (int i = 0; i < args.length; i++) {
            assertEquals(method.getParameterTypes()[i], args[i].getClass());
        }
    }

    @Test
    void preservesUnsignedBitsAndDistinguishesShortFromChar() throws NoSuchMethodException {
        Method method = ArgumentModule.class.getMethod("unsigned",
            byte.class, short.class, char.class, int.class, long.class);
        byte[] data = hex("""
            ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
            """);

        Object[] args = MethodCodec.decodeArguments(method, data);
        assertArrayEquals(new Object[] {(byte) -1, (short) -1, '\uffff', -1, -1L}, args);
        assertInstanceOf(Byte.class, args[0]);
        assertInstanceOf(Short.class, args[1]);
        assertInstanceOf(Character.class, args[2]);
        assertInstanceOf(Integer.class, args[3]);
        assertInstanceOf(Long.class, args[4]);
    }

    @Test
    void zeroParameterMethodsRequireAnEmptyPayload() throws NoSuchMethodException {
        Method method = ModuleTest.LabeledHardware.class.getMethod("getLabel");

        assertArrayEquals(new Object[0], MethodCodec.decodeArguments(method, new byte[0]));
        assertThrows(IllegalArgumentException.class,
            () -> MethodCodec.decodeArguments(method, hex("00")));
    }

    @Test
    void rejectsTrailingBytesAfterTheLastArgument() throws NoSuchMethodException {
        Method method = ModuleTest.LabeledHardware.class.getMethod("setLabel", String.class);

        assertThrows(IllegalArgumentException.class,
            () -> MethodCodec.decodeArguments(method, hex("01 00 00 00 41 00")));
    }

    @Test
    void rejectsMalformedLaterArguments() throws NoSuchMethodException {
        Method method = ArrayModule.class.getMethod("transfer",
            short.class, byte[].class, byte[].class, String[].class);
        String prefix = "ff ff 01 00 00 00 7f 00 00 00 00 01 00 00 00 ";

        for (String suffix : List.of("01 00", "01 00 00 00", "01 00 00 00 80")) {
            byte[] data = hex(prefix + suffix);
            assertThrows(IllegalArgumentException.class,
                () -> MethodCodec.decodeArguments(method, data));
        }
    }

    @Test
    void describesAnEmptyModule() {
        MethodTable table = MethodTable.create(new ModuleTest.EmptyModule());
        assertArrayEquals(hex("01 00 00 00"), MethodCodec.describe(table));
    }

    @Test
    void describesTheLabelModuleUsingTheAbiByteLayout() {
        MethodTable table = MethodTable.create(new ModuleTest.LabeledHardware());

        // Header, getLabel, getLength, setLabel. All integer fields are little-endian.
        byte[] expected = hex("""
                01 00 03 00
                00 00 00 08 67 65 74 4c 61 62 65 6c 00 0c
                01 00 00 09 67 65 74 4c 65 6e 67 74 68 00 07
                02 00 00 08 73 65 74 4c 61 62 65 6c 01 0c 00
                """);

        assertArrayEquals(expected, MethodCodec.describe(table));
    }

    @Test
    void describesAsyncFlagsAndArrayTypeCodes() {
        MethodTable table = MethodTable.create(new ArrayModule());

        // transfer(u16, i8[], u8[], string[]) -> u8[], with async bit 0 set.
        byte[] expected = hex("""
                01 00 01 00
                00 00 01 08 74 72 61 6e 73 66 65 72
                04 05 82 83 8c 83
                """);

        assertArrayEquals(expected, MethodCodec.describe(table));
    }

    @Test
    void changingOneDescriptionDoesNotAffectLaterDescriptions() {
        MethodTable table = MethodTable.create(new ModuleTest.LabeledHardware());
        byte[] description = MethodCodec.describe(table);
        byte[] expected = description.clone();

        description[0] = 0;
        description[description.length - 1] = (byte) 0xff;

        assertArrayEquals(expected, MethodCodec.describe(table));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
    }
}
