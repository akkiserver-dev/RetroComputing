package jp.akkiserver.rc.api.hardware.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.Hardware;

/**
 * Represents a hardware device attached to a machine slot.
 *
 * @author akki697222
 * @since 0.1.0
 */
public interface Device extends Hardware {
    /**
     * Returns the device's PCI-style identity and address.
     *
     * @return device identity
     */
    @Override
    DeviceInfo info();

    /**
     * Notifies the device that it has been attached to its slot.
     *
     * @param context machine services available to the attached device
     */
    default void attached(DeviceContext context) {
    }

    /**
     * Called on the safe tick thread before boot and after execution has stopped.
     *
     * @param powered whether the machine is powered
     */
    default void powerChanged(boolean powered) {
    }

    /**
     * Notifies the device that it has been detached from the machine.
     */
    default void detached() {
    }

    /**
     * Writes device-specific persistent state to {@code out}.
     *
     * @param out destination for saved state
     */
    default void save(DataCompound out) {
    }

    /**
     * Restores device-specific persistent state from {@code in}.
     *
     * @param in source of saved state
     */
    default void load(DataCompound in) {
    }

    /**
     * Returns the version of the state format written by {@link #save(DataCompound)}.
     *
     * @return device state format version
     */
    default int dataVersion() {
        return 1;
    }

    /**
     * Migrates saved data from an earlier version to the current format.
     *
     * @param data data to migrate
     * @param fromVersion version encoded by the saved data
     * @throws IllegalStateException if this device does not support that source version
     */
    default void migrate(DataCompound data, int fromVersion) {
        throw new IllegalStateException("no migration from v" + fromVersion);
    }
}
