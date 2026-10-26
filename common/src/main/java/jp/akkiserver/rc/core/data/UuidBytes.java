package jp.akkiserver.rc.core.data;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUIDs are opaque RFC-order bytes, independent of the ABI's numeric byte order.
 */
public final class UuidBytes {
    private UuidBytes() {
    }

    public static byte[] encode(UUID id) {
        return ByteBuffer.allocate(16)
            .putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits())
            .array();
    }

    public static UUID decode(byte[] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("Expected 16 UUID bytes");
        ByteBuffer data = ByteBuffer.wrap(bytes);
        return new UUID(data.getLong(), data.getLong());
    }
}
