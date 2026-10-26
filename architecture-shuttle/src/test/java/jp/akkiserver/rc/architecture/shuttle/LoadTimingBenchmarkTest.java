package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual pre-_start benchmark using the real adapter, transformer and native compiler.
 */
class LoadTimingBenchmarkTest {
    private static void report(String phase, long start) {
        System.err.printf(Locale.ROOT, "[load-benchmark] phase=%s seconds=%.6f%n",
            phase, (System.nanoTime() - start) / 1_000_000_000.0);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void coldAndReloadPreparation() throws Exception {
        String image = System.getProperty("rc.shuttle.loadBenchmarkImage");
        assumeTrue(image != null, "Opt in with -PloadBenchmarkImage=/absolute/path/kernel.wasm");
        long readStart = System.nanoTime();
        byte[] source = Files.readAllBytes(Path.of(image));
        report("file.read", readStart);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        System.err.printf("[load-benchmark] bytes=%d sha256=%s%n", source.length, hash);
        // No kernel entry point is called. An import here would mean that the
        // benchmark unexpectedly executed guest firmware during preparation.
        ArchitectureHost host = (ArchitectureHost) Proxy.newProxyInstance(
            ArchitectureHost.class.getClassLoader(), new Class<?>[]{ArchitectureHost.class},
            (proxy, method, arguments) -> {
                throw new AssertionError("Unexpected host call before _start: " + method.getName());
            });
        try (var factory = new ShuttleArchitectureFactory()) {
            for (String pass : new String[]{"cold", "reload"}) {
                System.err.println("[load-benchmark] pass=" + pass + " begin");
                long totalStart = System.nanoTime();
                long createStart = System.nanoTime();
                try (Architecture architecture = factory.create(host)) {
                    report(pass + ".factory-create", createStart);
                    long bootStart = System.nanoTime();
                    architecture.boot(source);
                    report(pass + ".boot-prepare", bootStart);
                    report(pass + ".ready-before-start", totalStart);
                    assertInstanceOf(ShuttleArchitecture.class, architecture);
                }
                System.err.println("[load-benchmark] pass=" + pass + " closed-without-start");
            }
        }
    }
}
