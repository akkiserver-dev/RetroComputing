package jp.akkiserver.rc.core.data;

import jp.akkiserver.rc.api.data.DataCompound;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft-independent compound using the same tag representations as binary NBT. Missing or
 * differently typed values return zero/false/null. Arrays and compounds are copied on both sides so
 * a captured state cannot be mutated through aliases.
 */
public final class MapDataCompound implements DataCompound {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public static MapDataCompound copyOf(DataCompound data) {
        Objects.requireNonNull(data, "data");
        if (data instanceof MapDataCompound map) return map.copy();
        try {
            return NbtIO.read(data.write());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot copy compound", e);
        }
    }

    static Object copyValue(Object value) {
        if (value instanceof MapDataCompound compound) return compound.copy();
        if (value instanceof DataCompound compound) return copyOf(compound);
        if (value instanceof NbtIO.ListValue(int type, java.util.List<Object> values1)) {
            return new NbtIO.ListValue(
                type, values1.stream().map(MapDataCompound::copyValue).toList());
        }
        if (!value.getClass().isArray()) return value;
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().componentType(), length);
        for (int i = 0; i < length; i++) {
            Object element = Array.get(value, i);
            Array.set(copy, i, copyValue(Objects.requireNonNull(element, "Null array element")));
        }
        return copy;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    public void remove(String key) {
        values.remove(Objects.requireNonNull(key));
    }

    public MapDataCompound copy() {
        MapDataCompound copy = new MapDataCompound();
        values.forEach((key, value) -> copy.values.put(key, copyValue(value)));
        return copy;
    }

    @Override
    public boolean contains(@Nonnull String key) {
        return values.containsKey(Objects.requireNonNull(key));
    }

    Map<String, Object> entries() {
        return values;
    }

    // Minecraft canonicalizes every empty TAG_LIST to element type END on write.
    // Such a list has no element type to reject, so it can represent an empty typed list.
    private boolean untypedEmpty(String key) {
        return values.get(key) instanceof NbtIO.ListValue list
            && list.type() == 0
            && list.values().isEmpty();
    }

    private void put(String key, Object value) {
        values.put(Objects.requireNonNull(key), copyValue(Objects.requireNonNull(value)));
    }

    @Override
    public void putInt(@Nonnull String key, int value) {
        put(key, value);
    }

    @Override
    public int getInt(String key) {
        return values.get(key) instanceof Integer value ? value : 0;
    }

    @Override
    public void putLong(@Nonnull String key, long value) {
        put(key, value);
    }

    @Override
    public long getLong(String key) {
        return values.get(key) instanceof Long value ? value : 0L;
    }

    @Override
    public void putShort(@Nonnull String key, short value) {
        put(key, value);
    }

    @Override
    public short getShort(String key) {
        return values.get(key) instanceof Short value ? value : (short) 0;
    }

    @Override
    public void putByte(@Nonnull String key, byte value) {
        put(key, value);
    }

    @Override
    public byte getByte(String key) {
        return values.get(key) instanceof Byte value ? value : (byte) 0;
    }

    @Override
    public void putFloat(@Nonnull String key, float value) {
        put(key, value);
    }

    @Override
    public float getFloat(String key) {
        return values.get(key) instanceof Float value ? value : 0f;
    }

    @Override
    public void putDouble(@Nonnull String key, double value) {
        put(key, value);
    }

    @Override
    public double getDouble(String key) {
        return values.get(key) instanceof Double value ? value : 0d;
    }

    @Override
    public void putString(@Nonnull String key, @Nonnull String value) {
        put(key, value);
    }

    @Override
    public String getString(String key) {
        return values.get(key) instanceof String value ? value : null;
    }

    @Override
    public void putBoolean(@Nonnull String key, boolean value) {
        put(key, (byte) (value ? 1 : 0));
    }

    @Override
    public boolean getBoolean(String key) {
        return getByte(key) != 0;
    }

    @Override
    public void putUUID(@Nonnull String key, @Nonnull UUID value) {
        Objects.requireNonNull(value);
        put(
            key,
            new int[]{
                (int) (value.getMostSignificantBits() >>> 32),
                (int) value.getMostSignificantBits(),
                (int) (value.getLeastSignificantBits() >>> 32),
                (int) value.getLeastSignificantBits()
            });
    }

    @Override
    public UUID getUUID(String key) {
        return values.get(key) instanceof int[] value && value.length == 4
            ? new UUID(
            (long) value[0] << 32 | Integer.toUnsignedLong(value[1]),
            (long) value[2] << 32 | Integer.toUnsignedLong(value[3]))
            : null;
    }

    @Override
    public void putCompound(@Nonnull String key, @Nonnull DataCompound value) {
        put(key, copyOf(value));
    }

    @Override
    public MapDataCompound getCompound(String key) {
        return values.get(key) instanceof MapDataCompound value ? value.copy() : null;
    }

    @Override
    public void putIntArray(String key, int[] value) {
        put(key, value);
    }

    @Override
    public int[] getIntArray(String key) {
        return values.get(key) instanceof int[] value ? (int[]) copyValue(value) : null;
    }

    @Override
    public void putLongArray(String key, long[] value) {
        put(key, value);
    }

    @Override
    public long[] getLongArray(String key) {
        return values.get(key) instanceof long[] value ? (long[]) copyValue(value) : null;
    }

    @Override
    public void putShortArray(String key, short[] value) {
        put(key, value);
    }

    @Override
    public short[] getShortArray(String key) {
        if (untypedEmpty(key)) return new short[0];
        return values.get(key) instanceof short[] value ? (short[]) copyValue(value) : null;
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        put(key, value);
    }

    @Override
    public byte[] getByteArray(String key) {
        return values.get(key) instanceof byte[] value ? (byte[]) copyValue(value) : null;
    }

    @Override
    public void putFloatArray(String key, float[] value) {
        put(key, value);
    }

    @Override
    public float[] getFloatArray(String key) {
        if (untypedEmpty(key)) return new float[0];
        return values.get(key) instanceof float[] value ? (float[]) copyValue(value) : null;
    }

    @Override
    public void putDoubleArray(String key, double[] value) {
        put(key, value);
    }

    @Override
    public double[] getDoubleArray(String key) {
        if (untypedEmpty(key)) return new double[0];
        return values.get(key) instanceof double[] value ? (double[]) copyValue(value) : null;
    }

    @Override
    public void putBooleanArray(String key, boolean[] value) {
        put(key, value);
    }

    @Override
    public boolean[] getBooleanArray(String key) {
        if (untypedEmpty(key)) return new boolean[0];
        return values.get(key) instanceof boolean[] value ? (boolean[]) copyValue(value) : null;
    }

    @Override
    public void putStringArray(String key, String[] value) {
        put(key, value);
    }

    @Override
    public String[] getStringArray(String key) {
        if (untypedEmpty(key)) return new String[0];
        return values.get(key) instanceof String[] value ? (String[]) copyValue(value) : null;
    }

    @Override
    public void putUUIDArray(String key, UUID[] value) {
        put(key, value);
    }

    @Override
    public UUID[] getUUIDArray(String key) {
        if (untypedEmpty(key)) return new UUID[0];
        return values.get(key) instanceof UUID[] value ? (UUID[]) copyValue(value) : null;
    }

    @Override
    public void putCompoundArray(String key, DataCompound[] value) {
        put(key, value);
    }

    @Override
    public DataCompound[] getCompoundArray(String key) {
        if (untypedEmpty(key)) return new DataCompound[0];
        return values.get(key) instanceof DataCompound[] value
            ? (DataCompound[]) copyValue(value)
            : null;
    }

    @Override
    public byte[] write() throws IOException {
        return NbtIO.write(this);
    }

    @Override
    public void write(OutputStream output) throws IOException {
        NbtIO.write(this, output);
    }
}
