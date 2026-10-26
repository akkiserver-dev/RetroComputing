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
import jp.akkiserver.rc.core.data.UuidBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Module
public final class DiskDevice implements Device {
    public static final int SECTOR_SIZE = 4096, MAX_TRANSFER = 256;
    private final DeviceInfo info;
    private final long sectors;
    private final int maxTransfer;
    private boolean readOnly;
    private DeviceStorage storage;

    public DiskDevice(UUID address, long sectors, boolean readOnly) {
        this(address, sectors, readOnly, MAX_TRANSFER);
    }

    public DiskDevice(UUID address, long sectors, boolean readOnly, int maxTransfer) {
        if (sectors < 1 || sectors > 0xffffffffL) throw new IllegalArgumentException("sectors");
        if (maxTransfer < 1 || maxTransfer > MAX_TRANSFER)
            throw new IllegalArgumentException("maxTransfer");
        this.info = new DeviceInfo(0x0300, 0, 0, address);
        this.sectors = sectors;
        this.readOnly = readOnly;
        this.maxTransfer = maxTransfer;
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    @Override
    public void attached(DeviceContext context) {
        storage = context.storage();
    }

    @Override
    public void detached() {
        storage = null;
    }

    @Export
    public @Unsigned byte[] getInfo() {
        UUID id = info.address();
        return ByteBuffer.allocate(28)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt((int) sectors)
            .putShort((short) SECTOR_SIZE)
            .putShort((short) maxTransfer)
            .putShort((short) (2 | (readOnly ? 4 : 0)))
            .putShort((short) 0)
                .put(UuidBytes.encode(id))
            .array();
    }

    private long range(int lba, int count) {
        long first = Integer.toUnsignedLong(lba);
        if (count < 1 || count > maxTransfer || first + count > sectors)
            throw new DeviceException(StdErrors.E_INVAL);
        if (storage == null) throw new DeviceException(StdErrors.E_NOMEDIA);
        return first * SECTOR_SIZE;
    }

    @Export(async = true)
    public @Unsigned byte[] read(@Unsigned int lba, @Unsigned short count) {
        int n = Short.toUnsignedInt(count);
        long offset = range(lba, n);
        byte[] data = new byte[n * SECTOR_SIZE];
        if (!storage.exists("disk.bin")) return data;
        try (SeekableByteChannel channel = storage.open("disk.bin", StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                int size = channel.read(buffer);
                if (size < 0) break;
                if (size == 0) throw new IOException("No read progress");
            }
            return data;
        } catch (IOException e) {
            throw new DeviceException(StdErrors.E_IO);
        }
    }

    @Export(async = true)
    public void write(@Unsigned int lba, @Unsigned byte[] data) {
        if (data.length % SECTOR_SIZE != 0) throw new DeviceException(StdErrors.E_INVAL);
        long offset = range(lba, data.length / SECTOR_SIZE);
        if (readOnly) throw new DeviceException(StdErrors.E_READONLY);
        try (SeekableByteChannel channel =
                 storage.open("disk.bin", StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining())
                if (channel.write(buffer) == 0) throw new IOException("No write progress");
        } catch (IOException e) {
            throw new DeviceException(StdErrors.E_IO);
        }
    }

    @Override
    public void save(DataCompound out) {
        out.putLong("sectors", sectors);
        out.putBoolean("readOnly", readOnly);
    }

    @Override
    public void load(DataCompound in) {
        if (in.getLong("sectors") != sectors)
            throw new IllegalArgumentException("Disk capacity changed");
        readOnly = in.getBoolean("readOnly");
    }
}
