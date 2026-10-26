package jp.akkiserver.rc.core.device;

import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.Module;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

@Module
public final class KeyboardDevice implements Device {
    private final DeviceInfo info;
    private volatile DeviceContext context;

    public KeyboardDevice(UUID address) {
        info = new DeviceInfo(0x0200, 0, 0, address);
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    @Override
    public void attached(DeviceContext context) {
        this.context = context;
    }

    @Override
    public void detached() {
        context = null;
    }

    /**
     * kind: 1 press, 2 release, 3 text input. Key and modifiers use GLFW codes.
     */
    public void key(int kind, int modifiers, int key, int codePoint) {
        if (kind < 1
            || kind > 3
            || (modifiers & ~255) != 0
            || (key & ~65535) != 0
            || !Character.isValidCodePoint(codePoint)
            || codePoint >= 0xd800 && codePoint <= 0xdfff)
            throw new IllegalArgumentException("Invalid key event");
        DeviceContext target = context;
        if (target != null)
            target.signal(
                ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) kind)
                    .put((byte) modifiers)
                    .putShort((short) key)
                    .putInt(codePoint)
                    .array());
    }
}
