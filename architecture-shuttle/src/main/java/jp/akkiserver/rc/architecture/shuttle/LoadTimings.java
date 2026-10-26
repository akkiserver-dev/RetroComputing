package jp.akkiserver.rc.architecture.shuttle;

import java.util.Locale;

/**
 * Opt-in host diagnostics; never emitted to the guest firmware log.
 */
final class LoadTimings {
    private static final boolean ENABLED = Boolean.getBoolean("rc.shuttle.loadTimings");

    private LoadTimings() {
    }

    static long start() {
        return ENABLED ? System.nanoTime() : 0;
    }

    static void finish(String phase, long start, String details) {
        if (start == 0)
            return;
        System.err.printf(Locale.ROOT, "[shuttle-load] phase=%s seconds=%.6f %s%n",
            phase, (System.nanoTime() - start) / 1_000_000_000.0, details);
    }
}
