package jp.akkiserver.rc.core.runtime;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Per-call encoder; all multibyte numbers use little-endian order.
 */
final class WireWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    void writeByte(int value) {
        out.write(value);
    }

    void writeShort(int value) {
        writeByte(value);
        writeByte(value >>> 8);
    }

    void writeInt(int value) {
        writeShort(value);
        writeShort(value >>> 16);
    }

    void writeLong(long value) {
        writeInt((int) value);
        writeInt((int) (value >>> 32));
    }

    void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    void writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
    }

    void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    void writeString(String value) {
        Objects.requireNonNull(value, "Device strings must not be null");
        try {
            var bytes =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            writeInt(bytes.remaining());
            while (bytes.hasRemaining()) out.write(bytes.get());
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-16 string", e);
        }
    }

    void write(Class<?> type, Object value) {
        MethodCodec.validateValue(type, value);
        if (type == void.class) return;
        if (type.isArray()) {
            int length = Array.getLength(value);
            writeInt(length);
            Class<?> element = type.componentType();
            for (int i = 0; i < length; i++) write(element, Array.get(value, i));
        } else if (type == boolean.class) writeBoolean((Boolean) value);
        else if (type == byte.class) writeByte((Byte) value);
        else if (type == short.class) writeShort((Short) value);
        else if (type == char.class) writeShort((Character) value);
        else if (type == int.class) writeInt((Integer) value);
        else if (type == long.class) writeLong((Long) value);
        else if (type == float.class) writeFloat((Float) value);
        else if (type == double.class) writeDouble((Double) value);
        else if (type == String.class) writeString((String) value);
        else throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }

    byte[] toByteArray() {
        return out.toByteArray();
    }
}
