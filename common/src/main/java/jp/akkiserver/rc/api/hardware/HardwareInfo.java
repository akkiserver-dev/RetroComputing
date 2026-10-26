package jp.akkiserver.rc.api.hardware;

import java.util.UUID;

/**
 * Identity shared by all hardware.
 *
 * @author akki697222
 * @since 0.1.0
 */
public interface HardwareInfo {
    /**
     * Returns the unique address assigned to this piece of hardware.
     *
     * @return unique hardware address
     */
    UUID address();
}
