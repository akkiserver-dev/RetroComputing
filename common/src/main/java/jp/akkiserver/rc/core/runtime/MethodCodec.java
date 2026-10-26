package jp.akkiserver.rc.core.runtime;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MethodCodec {
    private MethodCodec() {
    }

    public static Object[] decodeArguments(Method method, byte[] data) {
        return decodeArguments(method.getParameterTypes(), data);
    }

    public static Object[] decodeArguments(Class<?>[] params, byte[] data) {
        WireReader reader = new WireReader(data);
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = readValue(reader, params[i]);
        }
        reader.requireEnd();
        return args;
    }

    public static byte[] encodeArguments(Class<?>[] types, Object[] values) {
        validateArguments(types, values);
        WireWriter writer = new WireWriter();
        for (int i = 0; i < types.length; i++) writer.write(types[i], values[i]);
        return writer.toByteArray();
    }

    public static byte[] encodeReturn(Method method, Object value) {
        return encodeReturn(method.getReturnType(), value);
    }

    public static byte[] encodeReturn(Class<?> type, Object value) {
        WireWriter writer = new WireWriter();
        writer.write(type, value);
        return writer.toByteArray();
    }

    public static void validateArguments(Class<?>[] types, Object[] values) {
        Objects.requireNonNull(values, "values");
        if (types.length != values.length) throw new IllegalArgumentException("Argument count mismatch");
        for (int i = 0; i < types.length; i++) validateValue(types[i], values[i]);
    }

    public static void validateValue(Class<?> type, Object value) {
        Objects.requireNonNull(type, "type");
        if (type == void.class) {
            if (value != null) throw new IllegalArgumentException("void has no value");
            return;
        }
        WireTypeResolver.of(type, false);
        Objects.requireNonNull(value, "Device values must not be null");
        Class<?> expected = type.isPrimitive() ? switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "char" -> Character.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        } : type;
        if (value.getClass() != expected) throw new IllegalArgumentException("Expected " + expected.getTypeName());
        if (type == String[].class) {
            for (String text : (String[]) value) Objects.requireNonNull(text, "Null string element");
        }
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
}
