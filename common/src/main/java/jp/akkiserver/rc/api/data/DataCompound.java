package jp.akkiserver.rc.api.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A key-value data container supporting scalar and array values.
 *
 * @author akki697222
 * @since 0.1.0
 */
@ParametersAreNonnullByDefault
public interface DataCompound {
    /**
     * Returns whether a value is stored under {@code key}.
     *
     * @param key entry key
     * @return whether the entry exists
     */
    boolean contains(String key);

    /**
     * Stores a nested compound under {@code key}.
     *
     * @param key entry key
     * @param compound nested compound to store
     */
    void putCompound(String key, DataCompound compound);

    /**
     * Stores an integer value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putInt(String key, int value);

    /**
     * Stores a long value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putLong(String key, long value);

    /**
     * Stores a short value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putShort(String key, short value);

    /**
     * Stores a byte value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putByte(String key, byte value);

    /**
     * Stores a float value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putFloat(String key, float value);

    /**
     * Stores a double value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putDouble(String key, double value);

    /**
     * Stores a boolean value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putBoolean(String key, boolean value);

    /**
     * Stores a string value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putString(String key, String value);

    /**
     * Stores a UUID value under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putUUID(String key, UUID value);

    /**
     * Stores an array of nested compounds under {@code key}.
     *
     * @param key entry key
     * @param compound compound array to store
     */
    void putCompoundArray(String key, DataCompound[] compound);

    /**
     * Stores an integer array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putIntArray(String key, int[] value);

    /**
     * Stores a long array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putLongArray(String key, long[] value);

    /**
     * Stores a short array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putShortArray(String key, short[] value);

    /**
     * Stores a byte array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putByteArray(String key, byte[] value);

    /**
     * Stores a float array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putFloatArray(String key, float[] value);

    /**
     * Stores a double array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putDoubleArray(String key, double[] value);

    /**
     * Stores a boolean array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putBooleanArray(String key, boolean[] value);

    /**
     * Stores a string array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putStringArray(String key, String[] value);

    /**
     * Stores a UUID array under {@code key}.
     *
     * @param key entry key
     * @param value value to store
     */
    void putUUIDArray(String key, UUID[] value);

    /**
     * Returns the nested compound stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    DataCompound getCompound(String key);

    /**
     * Returns the integer stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    int getInt(String key);

    /**
     * Returns the long stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    long getLong(String key);

    /**
     * Returns the short stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    short getShort(String key);

    /**
     * Returns the byte stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    byte getByte(String key);

    /**
     * Returns the boolean stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    boolean getBoolean(String key);

    /**
     * Returns the float stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    float getFloat(String key);

    /**
     * Returns the double stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    double getDouble(String key);

    /**
     * Returns the string stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    String getString(String key);

    /**
     * Returns the UUID stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    UUID getUUID(String key);

    /**
     * Returns the compound array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    DataCompound[] getCompoundArray(String key);

    /**
     * Returns the integer array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    int[] getIntArray(String key);

    /**
     * Returns the long array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    long[] getLongArray(String key);

    /**
     * Returns the short array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    short[] getShortArray(String key);

    /**
     * Returns the byte array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    byte[] getByteArray(String key);

    /**
     * Returns the boolean array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    boolean[] getBooleanArray(String key);

    /**
     * Returns the float array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    float[] getFloatArray(String key);

    /**
     * Returns the double array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    double[] getDoubleArray(String key);

    /**
     * Returns the string array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    String[] getStringArray(String key);

    /**
     * Returns the UUID array stored under {@code key}.
     *
     * @param key entry key
     * @return value stored for the entry, using the implementation-defined result when absent or incompatible
     */
    UUID[] getUUIDArray(String key);

    /**
     * Encodes this container and returns the resulting bytes.
     *
     * @return encoded data
     * @throws IOException if encoding fails
     */
    byte[] write() throws IOException;

    /**
     * Encodes this container to {@code os}.
     *
     * @param os destination stream
     * @throws IOException if encoding or writing fails
     */
    void write(OutputStream os) throws IOException;
}
