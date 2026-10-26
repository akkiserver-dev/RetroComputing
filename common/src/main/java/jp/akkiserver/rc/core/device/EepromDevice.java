package jp.akkiserver.rc.core.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.BootSource;
import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.api.runtime.Unsigned;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

@Module
public final class EepromDevice implements Device, BootSource {
    public static final int CODE_CAPACITY = 65536, DATA_CAPACITY = 256;
    private final DeviceInfo info;
    private byte[] code, data = new byte[DATA_CAPACITY];
    private boolean locked;

    public EepromDevice(UUID address, byte[] code) {
        this.info = new DeviceInfo(0x0301, 0, 0, address);
        if (code.length > CODE_CAPACITY)
            throw new IllegalArgumentException("EEPROM image too large");
        this.code = code.clone();
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    @Override
    public byte[] bootImage() {
        return code.clone();
    }

    @Export
    public @Unsigned byte[] getInfo() {
        return ByteBuffer.allocate(10)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(CODE_CAPACITY)
            .putInt(code.length)
            .putShort((short) (locked ? 1 : 0))
            .array();
    }

    @Export
    public @Unsigned byte[] readCode() {
        return code.clone();
    }

    @Export
    public void flash(@Unsigned byte[] value) {
        if (locked) throw new DeviceException(StdErrors.E_READONLY);
        if (value.length > CODE_CAPACITY) throw new DeviceException(StdErrors.E_INVAL);
        code = value.clone();
    }

    @Export
    public @Unsigned byte[] readData() {
        return data.clone();
    }

    @Export
    public void writeData(@Unsigned byte[] value) {
        if (value.length > DATA_CAPACITY) throw new DeviceException(StdErrors.E_INVAL);
        System.arraycopy(value, 0, data, 0, value.length);
    }

    @Export
    public void lock() {
        locked = true;
    }

    @Override
    public void save(DataCompound out) {
        out.putByteArray("code", code);
        out.putByteArray("data", data);
        out.putBoolean("locked", locked);
    }

    @Override
    public void load(DataCompound in) {
        byte[] nextCode = in.getByteArray("code"), nextData = in.getByteArray("data");
        if (nextCode == null
            || nextData == null
            || nextCode.length > CODE_CAPACITY
            || nextData.length > DATA_CAPACITY)
            throw new IllegalArgumentException("Invalid EEPROM state");
        code = nextCode.clone();
        data = Arrays.copyOf(nextData, DATA_CAPACITY);
        locked = in.getBoolean("locked");
    }
}
