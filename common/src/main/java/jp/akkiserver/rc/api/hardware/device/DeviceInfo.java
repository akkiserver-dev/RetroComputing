package jp.akkiserver.rc.api.hardware.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.HardwareInfo;
import jp.akkiserver.rc.api.runtime.Unsigned;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Identifies a device by its class, vendor, device ID, and address.
 *
 * @param deviceClass unsigned 16-bit device class code
 * @param vendorId unsigned 16-bit vendor code
 * @param deviceId unsigned 16-bit device code
 * @param address unique device address
 * @author akki697222
 * @since 0.1.0
 */
public record DeviceInfo(@Unsigned int deviceClass, @Unsigned int vendorId, @Unsigned int deviceId, @Nonnull UUID address) implements HardwareInfo {
    /**
     * Validates the device identifiers and address.
     *
     * @param deviceClass unsigned 16-bit device class code
     * @param vendorId unsigned 16-bit vendor code
     * @param deviceId unsigned 16-bit device code
     * @param address unique device address
     * @throws IllegalArgumentException if any identifier does not fit in 16 bits
     * @throws NullPointerException if {@code address} is {@code null}
     */
    public DeviceInfo {
        if ((deviceClass | vendorId | deviceId) >>> 16 != 0) {
            throw new IllegalArgumentException("class/vendor/id must fit in 16 bits");
        }
        Objects.requireNonNull(address);
    }

    /**
     * Reads device identity fields from their named entries in {@code compound}.
     *
     * @param compound source data
     * @return the decoded device identity
     * @throws IllegalArgumentException if a required field is absent or the identity is invalid
     * @throws NullPointerException if the address value is not available as a UUID
     */
    public static DeviceInfo readFrom(DataCompound compound) {
        if (!compound.contains("class")
            || !compound.contains("vendor")
            || !compound.contains("id")
            || !compound.contains("addr")) {
            throw new IllegalArgumentException("Invalid or broken DataCompound");
        }
        return new DeviceInfo(
            compound.getInt("class"),
            compound.getInt("vendor"),
            compound.getInt("id"),
            compound.getUUID("addr")
        );
    }

    /**
     * Writes this identity to {@code compound} and returns that container.
     *
     * @param compound destination data
     * @return the supplied container after writing the identity fields
     * @throws NullPointerException if {@code compound} is {@code null}
     */
    public DataCompound writeTo(DataCompound compound) {
        compound.putInt("class", deviceClass);
        compound.putInt("vendor", vendorId);
        compound.putInt("id", deviceId);
        compound.putUUID("addr", address);
        return compound;
    }
}
