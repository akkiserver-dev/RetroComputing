package jp.akkiserver.rc.core.runtime;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public final class MethodCodec {
    public static Object[] decodeArguments(Method method, byte[] data) {
        Class<?>[] params = method.getParameterTypes();
        WireReader reader = new WireReader(data);
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = readValue(reader, params[i]);
        }
        reader.requireEnd();
        return args;
    }

    public static byte[] describe(MethodTable table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        /* HEADER */
        out.write(1); // version
        out.write(0); // reserved
        writeU16(out, table.methods().size());

        table.methods().forEach(m -> writeMethod(out, m));

        return out.toByteArray();
    }

    private static Object readValue(WireReader reader, Class<?> type) {
        return switch (type) {
            case Class<?> c when c == boolean.class -> reader.readBoolean();
            case Class<?> c when c == byte.class -> reader.readByte();
            case Class<?> c when c == short.class -> reader.readShort();
            case Class<?> c when c == int.class -> reader.readInt();
            case Class<?> c when c == long.class -> reader.readLong();
            case Class<?> c when c == char.class -> reader.readChar();
            case Class<?> c when c == float.class -> reader.readFloat();
            case Class<?> c when c == double.class -> reader.readDouble();
            case Class<?> c when c == String.class -> reader.readString();
            case Class<?> c when c == boolean[].class -> reader.readBooleanArray();
            case Class<?> c when c == byte[].class -> reader.readByteArray();
            case Class<?> c when c == short[].class -> reader.readShortArray();
            case Class<?> c when c == int[].class -> reader.readIntArray();
            case Class<?> c when c == long[].class -> reader.readLongArray();
            case Class<?> c when c == char[].class -> reader.readCharArray();
            case Class<?> c when c == float[].class -> reader.readFloatArray();
            case Class<?> c when c == double[].class -> reader.readDoubleArray();
            case Class<?> c when c == String[].class -> reader.readStringArray();
            default -> throw new IllegalArgumentException("Unsupported type: " + type.getName());
        };
    }

    private static void writeMethod(ByteArrayOutputStream out, MethodInfo info) {
        writeU16(out, info.id());
        out.write(info.async() ? 1 : 0);
        byte[] name = info.name().getBytes(StandardCharsets.US_ASCII);
        out.write(name.length);
        out.writeBytes(name);
        out.write(info.signature().params().size());
        for (WireType type : info.signature().params()) {
            out.write(type.code());
        }
        out.write(info.signature().ret().code());
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private MethodCodec() {
    }
}
