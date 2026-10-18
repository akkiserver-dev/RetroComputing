package jp.akkiserver.rc.api.hardware;

import java.util.UUID;

/**
 * Identity shared by all hardware.
 *
 * @since 0.1.0
 */
public interface HardwareInfo {
    /** Unique address of this piece of hardware. */
    UUID address();
}
