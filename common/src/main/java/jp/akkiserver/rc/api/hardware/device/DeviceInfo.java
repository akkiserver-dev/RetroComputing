package jp.akkiserver.rc.api.hardware.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.HardwareInfo;
import jp.akkiserver.rc.api.runtime.Unsigned;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * @since 0.1.0
 * @author akki697222
 */
public record DeviceInfo(@Unsigned int deviceClass, @Unsigned int vendorId, @Unsigned int deviceId, @Nonnull UUID address) implements HardwareInfo {
    public DeviceInfo {
        if ((deviceClass | vendorId | deviceId) >>> 16 != 0) {
            throw new IllegalArgumentException("class/vendor/id must fit in 16 bits");
        }
        Objects.requireNonNull(address);
    }

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

    public DataCompound writeTo(DataCompound compound) {
        compound.putInt("class", deviceClass);
        compound.putInt("vendor", vendorId);
        compound.putInt("id", deviceId);
        compound.putUUID("addr", address);
        return compound;
    }
}
