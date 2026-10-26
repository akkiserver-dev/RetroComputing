package jp.akkiserver.rc.core.machine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MachineWorkers {
    private MachineWorkers() {
    }

    /**
     * Native runtimes require platform threads and sufficient native stack space.
     */
    public static ExecutorService create(int threads, long stackBytes) {
        if (threads < 1 || stackBytes < 1024 * 1024) throw new IllegalArgumentException();
        AtomicInteger id = new AtomicInteger();
        return Executors.newFixedThreadPool(
            threads,
            task -> {
                Thread thread =
                    new Thread(
                        null, task, "rc-machine-" + id.incrementAndGet(), stackBytes);
                thread.setDaemon(true);
                return thread;
            });
    }
}
