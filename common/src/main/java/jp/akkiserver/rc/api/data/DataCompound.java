package jp.akkiserver.rc.api.data;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

@ParametersAreNonnullByDefault
public interface DataCompound {
    boolean contains(String key);

    void putCompound(String key, DataCompound compound);
    void putInt(String key, int value);
    void putLong(String key, long value);
    void putShort(String key, short value);
    void putByte(String key, byte value);
    void putFloat(String key, float value);
    void putDouble(String key, double value);
    void putBoolean(String key, boolean value);
    void putString(String key, String value);
    void putUUID(String key, UUID value);

    void putCompoundArray(String key, DataCompound[] compound);
    void putIntArray(String key, int[] value);
    void putLongArray(String key, long[] value);
    void putShortArray(String key, short[] value);
    void putByteArray(String key, byte[] value);
    void putFloatArray(String key, float[] value);
    void putDoubleArray(String key, double[] value);
    void putBooleanArray(String key, boolean[] value);
    void putStringArray(String key, String[] value);
    void putUUIDArray(String key, UUID[] value);

    DataCompound getCompound(String key);
    int getInt(String key);
    long getLong(String key);
    short getShort(String key);
    byte getByte(String key);
    boolean getBoolean(String key);
    float getFloat(String key);
    double getDouble(String key);
    String getString(String key);
    UUID getUUID(String key);

    DataCompound[] getCompoundArray(String key);
    int[] getIntArray(String key);
    long[] getLongArray(String key);
    short[] getShortArray(String key);
    byte[] getByteArray(String key);
    boolean[] getBooleanArray(String key);
    float[] getFloatArray(String key);
    double[] getDoubleArray(String key);
    String[] getStringArray(String key);
    UUID[] getUUIDArray(String key);

    byte[] write();
    void write(OutputStream os) throws IOException;
}
