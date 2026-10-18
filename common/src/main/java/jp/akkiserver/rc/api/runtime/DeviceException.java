package jp.akkiserver.rc.api.runtime;

/**
 * Thrown by a device method to return an error status to the guest.
 * Codes outside the standard ranges are reported to the guest as E_INTERNAL.
 *
 * @since 0.1.0
 * @author akki697222
 */
public class DeviceException extends RuntimeException {
    private final int code;

    public DeviceException(int code) {
        super("device error " + code);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
