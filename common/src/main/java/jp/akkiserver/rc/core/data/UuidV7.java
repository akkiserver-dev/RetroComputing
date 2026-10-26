package jp.akkiserver.rc.core.data;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUIDv7 identities. Copying an identity intentionally does not regenerate it.
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID create() {
        return create(System.currentTimeMillis());
    }

    static UUID create(long millis) {
        if (millis < 0 || millis > 0xffffffffffffL)
            throw new IllegalArgumentException("Timestamp outside UUIDv7 range");
        return new UUID(
            (millis << 16) | 0x7000 | (RANDOM.nextInt() & 0xfff),
            0x8000000000000000L | (RANDOM.nextLong() & 0x3fffffffffffffffL));
    }
}
