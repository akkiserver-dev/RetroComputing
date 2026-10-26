package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MethodInvokerTest {
    private int id(MethodTable table, String name) {
        return table.methods().stream()
            .filter(m -> m.name().equals(name))
            .findFirst()
            .orElseThrow()
            .id();
    }

    @Test
    void decodesInvokesAndEncodesARealMethod() {
        Target target = new Target();
        MethodTable table = MethodTable.create(target);
        CallResult result =
            new MethodInvoker()
                .invokeEncoded(
                    table,
                    id(table, "increment"),
                    hex("05 00 00 00 02 00 00 00 6f 6b"));
        assertEquals(0, result.status());
        assertEquals(7, result.value());
        assertArrayEquals(hex("07 00 00 00"), result.encoded());
        assertEquals(1, target.calls);
    }

    @Test
    void validatesTheWholeInputBeforeExecuting() {
        Target target = new Target();
        MethodTable table = MethodTable.create(target);
        for (String input :
            new String[]{
                "05 00 00 00 02 00 00 00 61",
                "05 00 00 00 01 00 00 00 80",
                "05 00 00 00 00 00 00 00 00"
            }) {
            assertEquals(
                StdErrors.E_INVAL,
                new MethodInvoker()
                    .invokeEncoded(table, id(table, "increment"), hex(input))
                    .status());
        }
        assertEquals(0, target.calls);
    }

    @Test
    void distinguishesDecodeFailuresFromDeviceExceptions() {
        MethodTable table = MethodTable.create(new Target());
        var reported = new ArrayList<Exception>();
        MethodInvoker invoker = new MethodInvoker(reported::add);
        assertEquals(
            StdErrors.E_INTERNAL,
            invoker.invoke(table, id(table, "invalid"), new Object[0]).status());
        assertEquals(1, reported.size());
        assertEquals(-257, invoker.invoke(table, id(table, "expected"), new Object[0]).status());
        assertEquals(
            StdErrors.E_INTERNAL,
            invoker.invoke(table, id(table, "badCode"), new Object[0]).status());
        assertEquals(
            StdErrors.E_INTERNAL,
            invoker.invoke(table, id(table, "missing"), new Object[0]).status());
        assertThrows(
            AssertionError.class,
            () -> invoker.invoke(table, id(table, "fatal"), new Object[0]));
    }

    @Test
    void rejectsUnknownMethodsAndWrongJavaTypesWithoutSideEffects() {
        Target target = new Target();
        MethodTable table = MethodTable.create(target);
        MethodInvoker invoker = new MethodInvoker();
        assertEquals(StdErrors.E_NOMETHOD, invoker.invoke(table, -1, new Object[0]).status());
        assertEquals(
            StdErrors.E_NOMETHOD,
            invoker.invoke(table, table.methods().size(), new Object[0]).status());
        assertEquals(
            StdErrors.E_INVAL,
            invoker.invoke(table, id(table, "increment"), new Object[]{5L, "ok"}).status());
        assertEquals(0, target.calls);
    }

    @Test
    void copiesDeviceResultsAndKeepsTargetsSeparateDespiteSharedMetadata() {
        Target first = new Target(), second = new Target();
        MethodTable a = MethodTable.create(first), b = MethodTable.create(second);
        new MethodInvoker().invoke(a, id(a, "increment"), new Object[]{1, ""});
        assertEquals(0, second.calls);
        byte[] bytes = {1, 2};
        CallResult result = new MethodInvoker().invoke(b, id(b, "bytes"), new Object[]{bytes});
        bytes[0] = 9;
        ((byte[]) result.value())[1] = 9;
        assertArrayEquals(new byte[]{1, 2}, (byte[]) result.value());
    }

    @Test
    void encodesEmptyAndUnsignedValuesWithExactWireBits() {
        assertArrayEquals(new byte[0], MethodCodec.encodeReturn(void.class, null));
        assertArrayEquals(hex("00 00 00 00"), MethodCodec.encodeReturn(String.class, ""));
        assertArrayEquals(hex("ff ff ff ff"), MethodCodec.encodeReturn(int.class, -1));
        assertArrayEquals(
            hex("01 00 00 00 ff ff"),
            MethodCodec.encodeReturn(char[].class, new char[]{'\uffff'}));
        assertThrows(
            NullPointerException.class, () -> MethodCodec.encodeReturn(String.class, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> MethodCodec.encodeReturn(String.class, "\ud800"));
    }

    private byte[] hex(String value) {
        return HexFormat.of().parseHex(value.replace(" ", ""));
    }

    @Module
    static class Target {
        int calls;

        @Export
        public int increment(int n, String text) {
            calls++;
            return n + text.length();
        }

        @Export
        public String missing() {
            return null;
        }

        @Export
        public void invalid() {
            throw new IllegalArgumentException("device bug");
        }

        @Export
        public void expected() {
            throw new DeviceException(-257);
        }

        @Export
        public void badCode() {
            throw new DeviceException(-50);
        }

        @Export
        public void fatal() {
            throw new AssertionError("fatal");
        }

        @Export
        public byte[] bytes(byte[] data) {
            return data;
        }
    }
}
