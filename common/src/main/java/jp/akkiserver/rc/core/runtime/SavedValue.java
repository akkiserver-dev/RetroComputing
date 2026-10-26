package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.core.data.MapDataCompound;

import java.util.Arrays;

/**
 * Closed type vocabulary for saved call results; never loads arbitrary classes from disk.
 */
public final class SavedValue {
    private static final Class<?>[] TYPES = {
        void.class,
        boolean.class,
        byte.class,
        short.class,
        char.class,
        int.class,
        long.class,
        float.class,
        double.class,
        String.class,
        boolean[].class,
        byte[].class,
        short[].class,
        char[].class,
        int[].class,
        long[].class,
        float[].class,
        double[].class,
        String[].class
    };

    private SavedValue() {
    }

    public static DataCompound save(CallResult result) {
        MapDataCompound data = new MapDataCompound();
        data.putInt("status", result.status());
        int type = Arrays.asList(TYPES).indexOf(result.type());
        if (type < 0) throw new IllegalArgumentException("Unsupported saved value");
        data.putInt("type", type);
        data.putByteArray("bytes", result.succeeded() ? result.encoded() : new byte[0]);
        return data;
    }

    public static CallResult load(DataCompound data) {
        int status = data.getInt("status"), index = data.getInt("type");
        if (index < 0 || index >= TYPES.length)
            throw new IllegalArgumentException("Invalid saved value type");
        if (status != 0) return CallResult.failure(status);
        Class<?> type = TYPES[index];
        byte[] bytes = data.getByteArray("bytes");
        if (bytes == null) throw new IllegalArgumentException("Missing saved value");
        if (type == void.class) {
            if (bytes.length != 0) throw new IllegalArgumentException("Invalid saved void");
            return CallResult.success(void.class, null);
        }
        return CallResult.success(type, MethodCodec.decodeArguments(new Class<?>[]{type}, bytes)[0]);
    }
}
