package jp.akkiserver.rc.api.runtime;

/**
 * Thrown by a device method to return an error status to the guest.
 * Codes outside the standard ranges are reported to the guest as E_INTERNAL.
 *
 * @author akki697222
 * @since 0.1.0
 */
public class DeviceException extends RuntimeException {
    /**
     * The status code returned to the guest runtime.
     */
    private final int code;

    /**
     * Creates an exception carrying the supplied device status code.
     *
     * @param code status code returned to the guest
     */
    public DeviceException(int code) {
        super("device error " + code);
        this.code = code;
    }

    /**
     * Returns the status code carried by this exception.
     *
     * @return device status code
     */
    public int code() {
        return code;
    }
}
