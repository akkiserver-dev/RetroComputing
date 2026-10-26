package jp.akkiserver.rc.core.data;

import jp.akkiserver.rc.api.data.DataCompound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Standard named-root binary NBT, gzip on write; raw and gzip accepted on read.
 */
public final class NbtIO {
    public static final int DEFAULT_MAX_BYTES = 256 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_LIST_ELEMENTS = 1_000_000;

    private NbtIO() {
    }

    public static byte[] write(MapDataCompound compound) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        write(compound, bytes);
        return bytes.toByteArray();
    }

    /**
     * Finishes the gzip member but does not close the supplied stream.
     */
    public static void write(MapDataCompound compound, OutputStream output) throws IOException {
        Objects.requireNonNull(compound);
        try (GZIPOutputStream gzip =
                 new GZIPOutputStream(
                     new FilterOutputStream(output) {
                         @Override
                         public void close() throws IOException {
                             flush();
                         }
                     })) {
            DataOutputStream data = new DataOutputStream(gzip);
            data.writeByte(10);
            data.writeUTF("");
            writeValue(data, compound, 0);
            data.flush();
        }
    }

    public static MapDataCompound read(byte[] data) throws IOException {
        return read(new ByteArrayInputStream(data));
    }

    public static MapDataCompound read(InputStream input) throws IOException {
        return read(input, DEFAULT_MAX_BYTES);
    }

    public static MapDataCompound read(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 4)
            throw new IllegalArgumentException("Invalid NBT size limit");
        PushbackInputStream source =
            new PushbackInputStream(
                new FilterInputStream(input) {
                    @Override
                    public void close() {
                    }
                },
                2);
        int first = source.read(), second = source.read();
        if (second >= 0)
            source.unread(second);
        if (first >= 0)
            source.unread(first);
        try (InputStream decoded =
                 first == 0x1f && second == 0x8b ? new GZIPInputStream(source) : source) {
            byte[] bytes = decoded.readNBytes(maxBytes);
            if (decoded.read() != -1)
                throw new IOException("NBT exceeds size limit");
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
            if (data.readUnsignedByte() != 10)
                throw new IOException("NBT root must be a compound");
            data.readUTF();
            MapDataCompound compound = (MapDataCompound) readValue(data, 10, 0);
            if (data.available() != 0)
                throw new IOException("Trailing NBT data");
            return compound;
        }
    }

    private static int type(Object value) throws IOException {
        if (value instanceof Byte)
            return 1;
        if (value instanceof Short)
            return 2;
        if (value instanceof Integer)
            return 3;
        if (value instanceof Long)
            return 4;
        if (value instanceof Float)
            return 5;
        if (value instanceof Double)
            return 6;
        if (value instanceof byte[])
            return 7;
        if (value instanceof String)
            return 8;
        if (value instanceof MapDataCompound)
            return 10;
        if (value instanceof int[])
            return 11;
        if (value instanceof long[])
            return 12;
        if (value instanceof ListValue
            || value instanceof short[]
            || value instanceof float[]
            || value instanceof double[]
            || value instanceof boolean[]
            || value instanceof String[]
            || value instanceof UUID[]
            || value instanceof DataCompound[]) return 9;
        throw new IOException("Unsupported NBT value: " + value.getClass());
    }

    private static ListValue list(Object value) {
        if (value instanceof ListValue list) return list;
        List<Object> elements = new ArrayList<>();
        int tag;
        switch (value) {
            case short[] array -> {
                tag = 2;
                for (short item : array) elements.add(item);
            }
            case float[] array -> {
                tag = 5;
                for (float item : array) elements.add(item);
            }
            case double[] array -> {
                tag = 6;
                for (double item : array) elements.add(item);
            }
            case boolean[] array -> {
                tag = 1;
                for (boolean item : array) elements.add((byte) (item ? 1 : 0));
            }
            case String[] array -> {
                tag = 8;
                Collections.addAll(elements, array);
            }
            case UUID[] array -> {
                tag = 11;
                for (UUID item : array) {
                    elements.add(new int[]{
                        (int) (item.getMostSignificantBits() >>> 32),
                        (int) item.getMostSignificantBits(),
                        (int) (item.getLeastSignificantBits() >>> 32),
                        (int) item.getLeastSignificantBits()
                    });
                }
            }
            default -> {
                tag = 10;
                for (var item : (DataCompound[]) value) elements.add(MapDataCompound.copyOf(item));
            }
        }
        return new ListValue(tag, elements);
    }

    private static void writeValue(DataOutputStream out, Object value, int depth)
        throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting too deep");
        switch (type(value)) {
            case 1 -> out.writeByte((Byte) value);
            case 2 -> out.writeShort((Short) value);
            case 3 -> out.writeInt((Integer) value);
            case 4 -> out.writeLong((Long) value);
            case 5 -> out.writeFloat((Float) value);
            case 6 -> out.writeDouble((Double) value);
            case 7 -> {
                byte[] bytes = (byte[]) value;
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                ListValue list = list(value);
                out.writeByte(list.type());
                out.writeInt(list.values().size());
                for (Object element : list.values()) {
                    if (type(element) != list.type()) throw new IOException("Mixed NBT list");
                    writeValue(out, element, depth + 1);
                }
            }
            case 10 -> {
                for (var entry : ((MapDataCompound) value).entries().entrySet()) {
                    out.writeByte(type(entry.getValue()));
                    out.writeUTF(entry.getKey());
                    writeValue(out, entry.getValue(), depth + 1);
                }
                out.writeByte(0);
            }
            case 11 -> {
                int[] array = (int[]) value;
                out.writeInt(array.length);
                for (int item : array) out.writeInt(item);
            }
            case 12 -> {
                long[] array = (long[]) value;
                out.writeInt(array.length);
                for (long item : array) out.writeLong(item);
            }
            default -> throw new IOException("Invalid tag");
        }
    }

    private static int length(DataInputStream input, int minimumBytes, boolean list)
        throws IOException {
        int count = input.readInt();
        if (count < 0 || (long) count * minimumBytes > input.available() || list && count > MAX_LIST_ELEMENTS)
            throw new IOException("Invalid NBT collection length");
        return count;
    }

    private static Object readValue(DataInputStream in, int tag, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting too deep");
        return switch (tag) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] array = new byte[length(in, 1, false)];
                in.readFully(array);
                yield array;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elementTag = in.readUnsignedByte();
                if (elementTag > 12) throw new IOException("Invalid NBT list tag");
                int width = switch (elementTag) {
                        case 0, 1, 10 -> 1;
                        case 2, 8 -> 2;
                        case 3, 5, 7, 11, 12 -> 4;
                        case 4, 6 -> 8;
                        case 9 -> 5;
                        default -> throw new IOException("Invalid NBT list tag");
                };
                int count = length(in, width, true);
                if (elementTag == 0 && count != 0) throw new IOException("Nonempty END list");
                List<Object> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) values.add(readValue(in, elementTag, depth + 1));
                yield materialize(elementTag, values);
            }
            case 10 -> {
                MapDataCompound compound = new MapDataCompound();
                for (; ; ) {
                    int next = in.readUnsignedByte();
                    if (next == 0) break;
                    if (next > 12) throw new IOException("Invalid NBT tag");
                    String key = in.readUTF();
                    if (compound.contains(key)) throw new IOException("Duplicate NBT key: " + key);
                    compound.entries().put(key, readValue(in, next, depth + 1));
                }
                yield compound;
            }
            case 11 -> {
                int[] array = new int[length(in, 4, false)];
                for (int i = 0; i < array.length; i++) array[i] = in.readInt();
                yield array;
            }
            case 12 -> {
                long[] array = new long[length(in, 8, false)];
                for (int i = 0; i < array.length; i++) array[i] = in.readLong();
                yield array;
            }
            default -> throw new IOException("Invalid NBT tag: " + tag);
        };
    }

    private static Object materialize(int tag, List<Object> values) {
        int count = values.size();
        switch (tag) {
            case 1: {
                boolean[] array = new boolean[count];
                for (int i = 0; i < count; i++) {
                    byte value = (Byte) values.get(i);
                    if (value != 0 && value != 1) return new ListValue(tag, List.copyOf(values));
                    array[i] = value != 0;
                }
                return array;
            }
            case 2: {
                short[] array = new short[count];
                for (int i = 0; i < count; i++) array[i] = (Short) values.get(i);
                return array;
            }
            case 5: {
                float[] array = new float[count];
                for (int i = 0; i < count; i++) array[i] = (Float) values.get(i);
                return array;
            }
            case 6: {
                double[] array = new double[count];
                for (int i = 0; i < count; i++) array[i] = (Double) values.get(i);
                return array;
            }
            case 8, 10:
                return values.toArray(Object[]::new);
            case 11: {
                UUID[] array = new UUID[count];
                for (int i = 0; i < count; i++) {
                    int[] value = (int[]) values.get(i);
                    if (value.length != 4) return new ListValue(tag, List.copyOf(values));
                    array[i] = new UUID(
                            (long) value[0] << 32 | Integer.toUnsignedLong(value[1]),
                            (long) value[2] << 32 | Integer.toUnsignedLong(value[3])
                    );
                }
                return array;
            }
            default:
                return new ListValue(tag, List.copyOf(values));
        }
    }

    record ListValue(int type, List<Object> values) {
    }
}
