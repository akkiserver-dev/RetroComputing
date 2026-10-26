package jp.akkiserver.rc.core.architecture;

public record ExecutionResult(Reason reason, long timeoutTicks) {
    public ExecutionResult {
        if (reason == null || timeoutTicks < 0 || (reason != Reason.WAITING && timeoutTicks != 0))
            throw new IllegalArgumentException("Invalid execution result");
    }

    public static ExecutionResult yielded() {
        return new ExecutionResult(Reason.YIELDED, 0);
    }

    /**
     * A zero timeout waits indefinitely.
     */
    public static ExecutionResult waiting(long ticks) {
        return new ExecutionResult(Reason.WAITING, ticks);
    }

    public static ExecutionResult stopped() {
        return new ExecutionResult(Reason.STOPPED, 0);
    }

    public static ExecutionResult reboot() {
        return new ExecutionResult(Reason.REBOOT, 0);
    }

    public enum Reason {
        YIELDED,
        WAITING,
        STOPPED,
        REBOOT
    }
}
