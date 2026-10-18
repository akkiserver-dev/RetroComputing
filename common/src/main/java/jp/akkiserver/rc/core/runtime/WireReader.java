package jp.akkiserver.rc.core.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class WireReader {
    private final ByteBuffer in;

    WireReader(byte[] data) {
        this.in = ByteBuffer.wrap(Objects.requireNonNull(data, "data")).order(ByteOrder.LITTLE_ENDIAN);
    }

    byte readByte() {
        requireRemaining(1);
        return in.get();
    }

    short readShort() {
        requireRemaining(2);
        return in.getShort();
    }

    int readInt() {
        requireRemaining(4);
        return in.getInt();
    }

    long readUInt() {
        return Integer.toUnsignedLong(readInt());
    }

    long readLong() {
        requireRemaining(8);
        return in.getLong();
    }

    char readChar() {
        requireRemaining(2);
        return in.getChar();
    }

    float readFloat() {
        requireRemaining(4);
        return in.getFloat();
    }

    double readDouble() {
        requireRemaining(8);
        return in.getDouble();
    }

    boolean readBoolean() {
        requireRemaining(1);
        int value = Byte.toUnsignedInt(in.get());
        return switch (value) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new IllegalArgumentException("Invalid boolean value: " + value);
        };
    }

    String readString() {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(readByteArray()))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8 string", e);
        }
    }

    byte[] readByteArray() {
        byte[] bytes = new byte[readArrayLength(1)];
        in.get(bytes);
        return bytes;
    }

    short[] readShortArray() {
        short[] shorts = new short[readArrayLength(2)];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = readShort();
        }
        return shorts;
    }

    int[] readIntArray() {
        int[] ints = new int[readArrayLength(4)];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = readInt();
        }
        return ints;
    }

    long[] readLongArray() {
        long[] longs = new long[readArrayLength(8)];
        for (int i = 0; i < longs.length; i++) {
            longs[i] = readLong();
        }
        return longs;
    }

    char[] readCharArray() {
        char[] chars = new char[readArrayLength(2)];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = readChar();
        }
        return chars;
    }

    float[] readFloatArray() {
        float[] floats = new float[readArrayLength(4)];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = readFloat();
        }
        return floats;
    }

    double[] readDoubleArray() {
        double[] doubles = new double[readArrayLength(8)];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = readDouble();
        }
        return doubles;
    }

    boolean[] readBooleanArray() {
        boolean[] booleans = new boolean[readArrayLength(1)];
        for (int i = 0; i < booleans.length; i++) {
            booleans[i] = readBoolean();
        }
        return booleans;
    }

    String[] readStringArray() {
        String[] strings = new String[readArrayLength(4)];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = readString();
        }
        return strings;
    }

    void requireEnd() {
        if (in.hasRemaining()) {
            throw new IllegalArgumentException("Unexpected trailing argument bytes: " + in.remaining());
        }
    }

    private int readArrayLength(int elementBytes) {
        long count = readUInt();
        requireRemaining(count * elementBytes);
        return (int) count;
    }

    private void requireRemaining(long bytes) {
        if (bytes >= 0 && bytes <= in.remaining()) return;
        throw new IllegalArgumentException("Invalid argument length: required=" + bytes + ", remaining=" + in.remaining());
    }
}
