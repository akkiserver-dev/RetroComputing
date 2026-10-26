package jp.akkiserver.rc.core.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.api.runtime.Unsigned;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.data.UuidBytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The drive and removable medium have separate identities. Insert/eject on the safe tick thread.
 */
@Module
public final class FloppyDevice implements Device {
    private final DeviceInfo info;
    private final Function<UUID, DeviceStorage> mediaStorage;
    private DeviceContext context;
    private DiskDevice medium;

    public FloppyDevice(UUID drive, Function<UUID, DeviceStorage> mediaStorage) {
        info = new DeviceInfo(0x0300, 0, 1, drive);
        this.mediaStorage = Objects.requireNonNull(mediaStorage);
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    private void attachMedium() {
        if (context == null || medium == null) return;
        DeviceStorage storage = Objects.requireNonNull(mediaStorage.apply(medium.info().address()));
        medium.attached(
            new DeviceContext() {
                public int slot() {
                    return context.slot();
                }

                public void signal(byte[] bytes) {
                    context.signal(bytes);
                }

                public DeviceStorage storage() {
                    return storage;
                }

                public boolean hasPower() {
                    return context.hasPower();
                }
            });
    }

    @Override
    public void attached(DeviceContext context) {
        this.context = context;
        attachMedium();
    }

    @Override
    public void detached() {
        if (medium != null) medium.detached();
        context = null;
    }

    public void insert(UUID id, long sectors, boolean readOnly) {
        if (medium != null) throw new IllegalStateException("Drive already contains media");
        DiskDevice disk = new DiskDevice(id, sectors, readOnly, 1);
        medium = disk;
        try {
            attachMedium();
        } catch (Throwable error) {
            medium = null;
            throw error;
        }
        signal(1, id);
    }

    public UUID eject() {
        if (medium == null) return null;
        UUID id = medium.info().address();
        medium.detached();
        medium = null;
        signal(2, id);
        return id;
    }

    private void signal(int kind, UUID id) {
        if (context != null)
            context.signal(
                ByteBuffer.allocate(17)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) kind)
                .put(UuidBytes.encode(id))
                    .array());
    }

    @Export
    public @Unsigned byte[] getInfo() {
        if (medium == null)
            return ByteBuffer.allocate(28)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)
                .putShort((short) 4096)
                .putShort((short) 1)
                .putShort((short) 1)
                .array();
        byte[] bytes = medium.getInfo();
        bytes[8] |= 1;
        return bytes;
    }

    private DiskDevice medium() {
        if (medium == null) throw new DeviceException(StdErrors.E_NOMEDIA);
        return medium;
    }

    @Export(async = true)
    public @Unsigned byte[] read(@Unsigned int lba, @Unsigned short count) {
        return medium().read(lba, count);
    }

    @Export(async = true)
    public void write(@Unsigned int lba, @Unsigned byte[] bytes) {
        medium().write(lba, bytes);
    }

    @Override
    public void save(DataCompound out) {
        out.putBoolean("present", medium != null);
        if (medium != null) {
            MapDataCompound state = new MapDataCompound();
            medium.save(state);
            state.putUUID("uuid", medium.info().address());
            out.putCompound("medium", state);
        }
    }

    @Override
    public void load(DataCompound in) {
        DiskDevice restored = null;
        if (in.getBoolean("present")) {
            DataCompound state = in.getCompound("medium");
            if (state == null) throw new IllegalArgumentException("Missing medium state");
            restored =
                new DiskDevice(
                    state.getUUID("uuid"),
                    state.getLong("sectors"),
                    state.getBoolean("readOnly"),
                    1);
        }
        if (medium != null) medium.detached();
        medium = restored;
        attachMedium();
    }
}
