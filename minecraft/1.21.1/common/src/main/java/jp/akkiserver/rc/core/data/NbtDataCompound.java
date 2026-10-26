package jp.akkiserver.rc.core.data;

import jp.akkiserver.rc.api.data.DataCompound;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class NbtDataCompound implements DataCompound {
    protected final CompoundTag tag;

    public NbtDataCompound(CompoundTag tag) {
        this.tag = tag;
    }

    public NbtDataCompound() {
        this(new CompoundTag());
    }

    private static CompoundTag toTag(DataCompound value) {
        Objects.requireNonNull(value);
        if (value instanceof NbtDataCompound nbt)
            return nbt.tag.copy();

        try {
            return NbtIo.readCompressed(
                new ByteArrayInputStream(value.write()), NbtAccounter.create(256L << 20));
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid compound data", error);
        }
    }

    private static int[] uuidInts(UUID value) {
        Objects.requireNonNull(value);
        long high = value.getMostSignificantBits(), low = value.getLeastSignificantBits();
        return new int[]{(int) (high >>> 32), (int) high, (int) (low >>> 32), (int) low};
    }

    private ListTag list(String key, int type) {
        Tag value = tag.get(key);
        if (!(value instanceof ListTag list))
            return null;

        return list.getElementType() == type || list.isEmpty() && list.getElementType() == 0
            ? list
            : null;
    }

    @Override
    public boolean contains(String key) {
        return tag.contains(key);
    }

    @Override
    public void putCompound(String key, DataCompound compound) {
        tag.put(key, toTag(compound));
    }

    @Override
    public void putInt(String key, int value) {
        tag.putInt(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        tag.putLong(key, value);
    }

    @Override
    public void putShort(String key, short value) {
        tag.putShort(key, value);
    }

    @Override
    public void putByte(String key, byte value) {
        tag.putByte(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        tag.putFloat(key, value);
    }

    @Override
    public void putDouble(String key, double value) {
        tag.putDouble(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    @Override
    public void putString(String key, String value) {
        tag.putString(key, Objects.requireNonNull(value));
    }

    @Override
    public void putUUID(String key, UUID value) {
        tag.putUUID(key, Objects.requireNonNull(value));
    }

    @Override
    public void putCompoundArray(String key, DataCompound[] compound) {
        ListTag list = new ListTag();
        for (DataCompound item : compound)
            list.add(toTag(item));

        tag.put(key, list);
    }

    @Override
    public void putIntArray(String key, int[] value) {
        tag.putIntArray(key, value.clone());
    }

    @Override
    public void putLongArray(String key, long[] value) {
        tag.putLongArray(key, value.clone());
    }

    @Override
    public void putShortArray(String key, short[] value) {
        ListTag list = new ListTag();
        for (short item : value)
            list.add(ShortTag.valueOf(item));

        tag.put(key, list);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        tag.putByteArray(key, value.clone());
    }

    @Override
    public void putFloatArray(String key, float[] value) {
        ListTag list = new ListTag();
        for (float item : value)
            list.add(FloatTag.valueOf(item));

        tag.put(key, list);
    }

    @Override
    public void putDoubleArray(String key, double[] value) {
        ListTag list = new ListTag();
        for (double item : value)
            list.add(DoubleTag.valueOf(item));

        tag.put(key, list);
    }

    @Override
    public void putBooleanArray(String key, boolean[] value) {
        ListTag list = new ListTag();
        for (boolean item : value)
            list.add(ByteTag.valueOf((byte) (item ? 1 : 0)));

        tag.put(key, list);
    }

    @Override
    public void putStringArray(String key, String[] value) {
        ListTag list = new ListTag();
        for (String item : value)
            list.add(StringTag.valueOf(item));

        tag.put(key, list);
    }

    @Override
    public void putUUIDArray(String key, UUID[] value) {
        ListTag list = new ListTag();
        for (UUID item : value)
            list.add(new IntArrayTag(uuidInts(item)));

        tag.put(key, list);
    }

    @Override
    public DataCompound getCompound(String key) {
        return tag.contains(key, Tag.TAG_COMPOUND)
            ? new NbtDataCompound(tag.getCompound(key).copy())
            : null;
    }

    @Override
    public int getInt(String key) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : 0;
    }

    @Override
    public long getLong(String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : 0;
    }

    @Override
    public short getShort(String key) {
        return tag.contains(key, Tag.TAG_SHORT) ? tag.getShort(key) : (short) 0;
    }

    @Override
    public byte getByte(String key) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getByte(key) : (byte) 0;
    }

    @Override
    public boolean getBoolean(String key) {
        return tag.contains(key, Tag.TAG_BYTE) && tag.getBoolean(key);
    }

    @Override
    public float getFloat(String key) {
        return tag.contains(key, Tag.TAG_FLOAT) ? tag.getFloat(key) : 0;
    }

    @Override
    public double getDouble(String key) {
        return tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : 0;
    }

    @Override
    public String getString(String key) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
    }

    @Override
    public UUID getUUID(String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    @Override
    public DataCompound[] getCompoundArray(String key) {
        ListTag list = list(key, Tag.TAG_COMPOUND);
        if (list == null)
            return null;

        DataCompound[] result = new DataCompound[list.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = new NbtDataCompound(list.getCompound(i).copy());

        return result;
    }

    @Override
    public int[] getIntArray(String key) {
        return tag.contains(key, Tag.TAG_INT_ARRAY) ? tag.getIntArray(key).clone() : null;
    }

    @Override
    public long[] getLongArray(String key) {
        return tag.contains(key, Tag.TAG_LONG_ARRAY) ? tag.getLongArray(key).clone() : null;
    }

    @Override
    public short[] getShortArray(String key) {
        ListTag list = list(key, Tag.TAG_SHORT);
        if (list == null)
            return null;

        short[] result = new short[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.getShort(i);
        }
        return result;
    }

    @Override
    public byte[] getByteArray(String key) {
        return tag.contains(key, Tag.TAG_BYTE_ARRAY) ? tag.getByteArray(key).clone() : null;
    }

    @Override
    public boolean[] getBooleanArray(String key) {
        ListTag list = list(key, Tag.TAG_BYTE);
        if (list == null)
            return null;

        boolean[] result = new boolean[list.size()];
        for (int i = 0; i < result.length; i++) {
            byte value = ((ByteTag) list.get(i)).getAsByte();
            if (value != 0 && value != 1)
                return null;

            result[i] = value != 0;
        }
        return result;
    }

    @Override
    public float[] getFloatArray(String key) {
        ListTag list = list(key, Tag.TAG_FLOAT);
        if (list == null)
            return null;

        float[] result = new float[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.getFloat(i);
        }
        return result;
    }

    @Override
    public double[] getDoubleArray(String key) {
        ListTag list = list(key, Tag.TAG_DOUBLE);
        if (list == null)
            return null;

        double[] result = new double[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.getDouble(i);
        }
        return result;
    }

    @Override
    public String[] getStringArray(String key) {
        ListTag list = list(key, Tag.TAG_STRING);
        if (list == null)
            return null;

        String[] result = new String[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.getString(i);
        }
        return result;
    }

    @Override
    public UUID[] getUUIDArray(String key) {
        ListTag list = list(key, Tag.TAG_INT_ARRAY);
        if (list == null)
            return null;

        UUID[] result = new UUID[list.size()];
        for (int i = 0; i < result.length; i++) {
            int[] parts = ((IntArrayTag) list.get(i)).getAsIntArray();
            if (parts.length != 4)
                return null;

            result[i] =
                new UUID(
                    (long) parts[0] << 32 | Integer.toUnsignedLong(parts[1]),
                    (long) parts[2] << 32 | Integer.toUnsignedLong(parts[3]));
        }
        return result;
    }

    @Override
    public byte[] write() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out);
        return out.toByteArray();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        NbtIo.writeCompressed(
            tag,
            new FilterOutputStream(os) {
                @Override
                public void close() throws IOException {
                    flush();
                }
            });
    }
}
