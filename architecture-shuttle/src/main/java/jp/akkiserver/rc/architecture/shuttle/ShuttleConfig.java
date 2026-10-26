package jp.akkiserver.rc.architecture.shuttle;

import java.nio.charset.StandardCharsets;

public record ShuttleConfig(
    String vendor, String model, long memoryBytes, int maxImages, int maxChildren) {
    public ShuttleConfig {
        if (vendor == null
            || model == null
            || vendor.getBytes(StandardCharsets.UTF_8).length > 64
            || model.getBytes(StandardCharsets.UTF_8).length > 64
            || memoryBytes < 131072
            || maxImages < 1
            || maxChildren < 1)
            throw new IllegalArgumentException("Invalid CPU configuration");
    }

    public static ShuttleConfig defaults() {
        return new ShuttleConfig("Retro Computings", "Shuttle", 64L << 20, 64, 32);
    }
}
