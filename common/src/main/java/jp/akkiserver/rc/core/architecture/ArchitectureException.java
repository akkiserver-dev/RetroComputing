package jp.akkiserver.rc.core.architecture;

import jp.akkiserver.rc.api.runtime.StdErrors;

/**
 * A fatal guest or host failure, recorded as the next boot's reset reason.
 */
public final class ArchitectureException extends Exception {
    private final int resetReason;

    public ArchitectureException(int resetReason, Throwable cause) {
        super(cause);
        if (resetReason != StdErrors.E_TRAP && resetReason != StdErrors.E_MCHK)
            throw new IllegalArgumentException();
        this.resetReason = resetReason;
    }

    public int resetReason() {
        return resetReason;
    }
}
