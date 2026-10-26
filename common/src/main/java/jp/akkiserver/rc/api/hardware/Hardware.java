package jp.akkiserver.rc.api.hardware;

/**
 * Common identity contract for hardware exposed by the API.
 *
 * @author akki697222
 * @since 0.1.0
 */
public interface Hardware {
    /**
     * Returns the identifying information for this hardware.
     *
     * @return identifying information for this hardware
     */
    HardwareInfo info();
}
