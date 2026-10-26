package jp.akkiserver.rc.api.hardware.device;

/**
 * What the machine gives a device when it is attached to a slot.
 *
 * @author akki697222
 * @since 0.1.0
 */
public interface DeviceContext {
    /**
     * The slot this device is attached to.
     *
     * @return slot index
     */
    int slot();

    /**
     * Queues a SIGNAL event for the guest. May be called from any thread.
     *
     * @param payload the event payload; copied before this method returns
     */
    void signal(byte[] payload);

    /**
     * Storage reserved for this device's UUID.
     *
     * @return device-specific storage
     */
    DeviceStorage storage();

    /**
     * Whether the machine is currently powered.
     *
     * @return whether machine power is on
     */
    boolean hasPower();
}
