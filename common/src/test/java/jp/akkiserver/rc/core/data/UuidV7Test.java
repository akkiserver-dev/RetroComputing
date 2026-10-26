package jp.akkiserver.rc.core.data;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidV7Test {
    @Test
    void uuidBytesUseRfcOrder() {
        var id = UUID.fromString("00112233-4455-7788-99aa-bbccddeeff00");
        assertArrayEquals(
            HexFormat.of().parseHex("001122334455778899aabbccddeeff00"),
            UuidBytes.encode(id));
        assertEquals(id, UuidBytes.decode(UuidBytes.encode(id)));
    }

    @Test
    void timestampVersionVariantAndUniqueness() {
        var ids = new HashSet<UUID>();
        long millis = 1792600000000L;
        for (int i = 0; i < 1000; i++) {
            var id = UuidV7.create(millis);
            assertEquals(7, id.version());
            assertEquals(2, id.variant());
            assertEquals(millis, id.getMostSignificantBits() >>> 16);
            assertTrue(ids.add(id));
        }
    }
}
