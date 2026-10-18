package jp.akkiserver.rc.api.hardware.device;

/**
 * What the machine gives a device when it is attached to a slot.
 *
 * @since 0.1.0
 * @author akki697222
 */
public interface DeviceContext {
    /** The slot this device is attached to. */
    int slot();

    /**
     * Queues a SIGNAL event for the guest. May be called from any thread.
     *
     * @param payload the event payload; copied before this method returns
     */
    void signal(byte[] payload);

    /** Storage reserved for this device's UUID. */
    DeviceStorage storage();

    /** Whether the machine is currently powered. */
    boolean hasPower();
}
