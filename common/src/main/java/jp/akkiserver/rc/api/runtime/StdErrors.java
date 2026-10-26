package jp.akkiserver.rc.api.runtime;

/**
 * Stable status codes shared by device implementations and guest frontends.
 *
 * @author akki697222
 */
public final class StdErrors {
    /**
     * Invalid argument status.
     */
    public static final int E_INVAL = -1;

    /**
     * Fault status.
     */
    public static final int E_FAULT = -2;

    /**
     * Missing device status.
     */
    public static final int E_NODEV = -3;

    /**
     * Missing method status.
     */
    public static final int E_NOMETHOD = -4;

    /**
     * Invalid handle status.
     */
    public static final int E_BADHANDLE = -5;

    /**
     * Insufficient memory status.
     */
    public static final int E_NOMEM = -6;

    /**
     * Unsupported operation status.
     */
    public static final int E_UNSUPPORTED = -7;

    /**
     * Version mismatch status.
     */
    public static final int E_VERSION = -8;

    /**
     * Retry status.
     */
    public static final int E_AGAIN = -9;

    /**
     * Insufficient power status.
     */
    public static final int E_NOPOWER = -10;

    /**
     * I/O failure status.
     */
    public static final int E_IO = -11;

    /**
     * Internal failure status.
     */
    public static final int E_INTERNAL = -12;

    /**
     * Machine-check status.
     */
    public static final int E_MCHK = -13;

    /**
     * Trap status.
     */
    public static final int E_TRAP = -14;

    /**
     * Missing media status.
     */
    public static final int E_NOMEDIA = -256;

    /**
     * Read-only media status.
     */
    public static final int E_READONLY = -257;

    private StdErrors() {
    }

    /**
     * Returns whether {@code code} is within a recognized standard error range.
     *
     * @param code status code to check
     * @return whether the code belongs to a recognized standard range
     */
    public static boolean isValid(int code) {
        return code >= E_TRAP && code <= E_INVAL || code <= -256;
    }
}
