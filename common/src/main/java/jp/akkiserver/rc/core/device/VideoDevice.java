package jp.akkiserver.rc.core.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.api.runtime.Unsigned;
import jp.akkiserver.rc.api.video.CardSpec;
import jp.akkiserver.rc.api.video.Monitor;
import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.api.video.VideoMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side VRAM. Mutate on the machine's safe tick thread. Client updates are budgeted deltas.
 */
@Module
public final class VideoDevice implements Device {
    private final DeviceInfo info;
    private final CardSpec spec;
    private final int[] palette = new int[256];
    private final BitSet dirty = new BitSet();
    private DeviceContext context;
    private Monitor monitor;
    private List<VideoMode> modes = List.of();
    private int current = -1, cursorX, cursorY;
    private long displayGeneration;
    private boolean cursorVisible;
    private byte[] vram = new byte[0];

    public VideoDevice(UUID id, CardSpec spec) {
        this.info = new DeviceInfo(0x0100, 0, 0, id);
        this.spec = Objects.requireNonNull(spec);
        for (int i = 0; i < 256; i++) palette[i] = i * 0x010101;
    }

    @Override
    public DeviceInfo info() {
        return info;
    }

    @Override
    public void powerChanged(boolean powered) {
        if (powered) reset(modes.isEmpty() ? -1 : 0);
    }

    @Override
    public void attached(DeviceContext context) {
        this.context = context;
    }

    @Override
    public void detached() {
        context = null;
    }

    public void connect(Monitor monitor) {
        this.monitor = monitor;
        modes =
            monitor == null
                ? List.of()
                : spec.modes().stream()
                .filter(
                    m ->
                        spec.formats().contains(m.format())
                            && m.bytes() <= spec.vramBytes()
                            && monitor.accepts(
                            m.format().text(),
                            m.width(),
                            m.height()))
                .sorted(
                    Comparator.comparing((VideoMode m) -> !m.format().text())
                        .thenComparingInt(VideoMode::bytes)
                        .thenComparingInt(m -> m.format().code())
                        .thenComparingInt(VideoMode::width)
                        .thenComparingInt(VideoMode::height))
                .toList();
        reset(modes.isEmpty() ? -1 : 0);
        if (context != null)
            context.signal(
                new byte[]{
                    (byte) (monitor == null ? 2 : 1),
                    (byte) (monitor != null && monitor.monochrome() ? 1 : 0)
                });
    }

    private void reset(int index) {
        displayGeneration++;
        current = index;
        vram = index < 0 ? new byte[0] : new byte[modes.get(index).bytes()];
        dirty.clear();
        dirty.set(0, vram.length);
        cursorX = cursorY = 0;
        cursorVisible = false;
    }

    private VideoMode mode() {
        if (current < 0) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        return modes.get(current);
    }

    @Export
    public @Unsigned byte[] getInfo() {
        int mask = 0;
        for (PixelFormat format : spec.formats()) mask |= 1 << format.ordinal();
        int flags =
            (spec.hardwareDraw() ? 1 : 0)
                | (monitor != null ? 2 : 0)
                | (monitor != null && monitor.monochrome() ? 4 : 0);
        return ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(spec.vramBytes())
            .putInt(spec.bandwidthPerTick())
            .putInt(mask)
            .putShort((short) flags)
            .putShort((short) 0)
            .array();
    }

    @Export
    public @Unsigned byte[] modes() {
        ByteBuffer b = ByteBuffer.allocate(modes.size() * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < modes.size(); i++) {
            VideoMode m = modes.get(i);
            b.putShort((short) m.id())
                .put((byte) m.format().code())
                .put((byte) 0)
                .putShort((short) m.width())
                .putShort((short) m.height());
        }
        return b.array();
    }

    @Export
    public @Unsigned short getMode() {
        return current < 0 ? (short) 0xffff : (short) mode().id();
    }

    @Export
    public void setMode(@Unsigned short id) {
        int index = -1;
        for (int i = 0; i < modes.size(); i++)
            if (modes.get(i).id() == Short.toUnsignedInt(id)) index = i;
        if (modes.isEmpty()) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        if (index < 0) throw new DeviceException(StdErrors.E_INVAL);
        reset(index);
    }

    private int[] rect(short x, short y, short w, short h) {
        VideoMode m = mode();
        int[] r = {
            Short.toUnsignedInt(x),
            Short.toUnsignedInt(y),
            Short.toUnsignedInt(w),
            Short.toUnsignedInt(h)
        };
        if (r[0] + r[2] > m.width() || r[1] + r[3] > m.height())
            throw new DeviceException(StdErrors.E_INVAL);
        return r;
    }

    private int value(byte[] bytes, int rowWidth, int x, int y) {
        PixelFormat f = mode().format();
        int row = f.rowBytes(rowWidth), bits = f.bits(), offset = y * row + x * bits / 8;
        if (bits < 8) return (bytes[offset] >>> (8 - bits - (x * bits % 8))) & ((1 << bits) - 1);
        int value = 0;
        for (int i = 0; i < bits / 8; i++)
            value |= Byte.toUnsignedInt(bytes[offset + i]) << (8 * i);
        return value;
    }

    private void value(byte[] bytes, int rowWidth, int x, int y, int value) {
        PixelFormat f = mode().format();
        int row = f.rowBytes(rowWidth), bits = f.bits(), offset = y * row + x * bits / 8;
        if (bits < 8) {
            int shift = 8 - bits - (x * bits % 8), mask = ((1 << bits) - 1) << shift;
            bytes[offset] = (byte) ((bytes[offset] & ~mask) | ((value << shift) & mask));
        } else for (int i = 0; i < bits / 8; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }

    private void mark(int x, int y, int w, int h) {
        if (w == 0 || h == 0) return;
        PixelFormat f = mode().format();
        int row = f.rowBytes(mode().width());
        for (int i = 0; i < h; i++)
            dirty.set(
                (y + i) * row + x * f.bits() / 8,
                (y + i) * row + (int) (((long) (x + w) * f.bits() + 7) / 8));
    }

    @Export
    public void blit(
        @Unsigned short x,
        @Unsigned short y,
        @Unsigned short w,
        @Unsigned short h,
        @Unsigned byte[] bytes) {
        int[] r = rect(x, y, w, h);
        if ((long) mode().format().rowBytes(r[2]) * r[3] != bytes.length)
            throw new DeviceException(StdErrors.E_INVAL);
        for (int yy = 0; yy < r[3]; yy++)
            for (int xx = 0; xx < r[2]; xx++)
                value(vram, mode().width(), r[0] + xx, r[1] + yy, value(bytes, r[2], xx, yy));
        mark(r[0], r[1], r[2], r[3]);
    }

    @Export
    public @Unsigned byte[] read(
        @Unsigned short x, @Unsigned short y, @Unsigned short w, @Unsigned short h) {
        int[] r = rect(x, y, w, h);
        byte[] result = new byte[mode().format().rowBytes(r[2]) * r[3]];
        for (int yy = 0; yy < r[3]; yy++)
            for (int xx = 0; xx < r[2]; xx++)
                value(result, r[2], xx, yy, value(vram, mode().width(), r[0] + xx, r[1] + yy));
        return result;
    }

    @Export
    public void write(@Unsigned short x, @Unsigned short y, String text, @Unsigned short attr) {
        if (!mode().format().text()) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        int cells;
        try {
            cells = TextCells.width(text);
        } catch (IllegalArgumentException error) {
            throw new DeviceException(StdErrors.E_INVAL);
        }
        if (cells > 65535) throw new DeviceException(StdErrors.E_INVAL);
        int[] r = rect(x, y, (short) cells, (short) 1);
        int column = r[0];
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int attribute = Short.toUnsignedInt(attr) << 16;
            value(vram, mode().width(), column++, r[1], character | attribute);
            if (TextCells.width(character) == 2)
                value(vram, mode().width(), column++, r[1], attribute);
        }
        mark(r[0], r[1], r[2], 1);
    }

    @Export
    public void setPalette(@Unsigned byte index, @Unsigned int rgb) {
        if ((rgb & 0xff000000) != 0) throw new DeviceException(StdErrors.E_INVAL);
        palette[Byte.toUnsignedInt(index)] = rgb;
    }

    @Export
    public @Unsigned int getPalette(@Unsigned byte index) {
        return palette[Byte.toUnsignedInt(index)];
    }

    @Export
    public void setCursor(@Unsigned short x, @Unsigned short y, boolean visible) {
        if (!mode().format().text()) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        int[] r = rect(x, y, (short) 1, (short) 1);
        cursorX = r[0];
        cursorY = r[1];
        cursorVisible = visible;
    }

    @Export
    public void fill(
        @Unsigned short x,
        @Unsigned short y,
        @Unsigned short w,
        @Unsigned short h,
        @Unsigned int v) {
        if (!spec.hardwareDraw()) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        int[] r = rect(x, y, w, h);
        for (int yy = 0; yy < r[3]; yy++)
            for (int xx = 0; xx < r[2]; xx++) value(vram, mode().width(), r[0] + xx, r[1] + yy, v);
        mark(r[0], r[1], r[2], r[3]);
    }

    @Export
    public void copy(
        @Unsigned short x,
        @Unsigned short y,
        @Unsigned short w,
        @Unsigned short h,
        short dx,
        short dy) {
        if (!spec.hardwareDraw()) throw new DeviceException(StdErrors.E_UNSUPPORTED);
        int[] r = rect(x, y, w, h);
        byte[] original = vram.clone();
        VideoMode m = mode();
        for (int yy = 0; yy < r[3]; yy++)
            for (int xx = 0; xx < r[2]; xx++) {
                int tx = r[0] + xx + dx, ty = r[1] + yy + dy;
                if (tx >= 0 && ty >= 0 && tx < m.width() && ty < m.height()) {
                    value(
                        vram,
                        m.width(),
                        tx,
                        ty,
                        value(original, m.width(), r[0] + xx, r[1] + yy));
                    mark(tx, ty, 1, 1);
                }
            }
    }

    /**
     * Drain once per client synchronization tick. A full display may take several ticks.
     */
    public List<Update> drainUpdates() {
        List<Update> updates = new ArrayList<>();
        int budget = spec.bandwidthPerTick(), start = dirty.nextSetBit(0);
        while (start >= 0 && budget > 0) {
            int end = Math.min(dirty.nextClearBit(start), start + budget);
            updates.add(new Update(start, Arrays.copyOfRange(vram, start, end)));
            dirty.clear(start, end);
            budget -= end - start;
            start = dirty.nextSetBit(end);
        }
        return List.copyOf(updates);
    }

    public DisplayState captureDisplay() {
        return new DisplayState(displayGeneration, current < 0 ? null : mode(), palette, drainUpdates(),
            cursorX, cursorY, cursorVisible, monitor != null && monitor.monochrome());
    }

    public int[] palette() {
        return palette.clone();
    }

    public int cursorX() {
        return cursorX;
    }

    public int cursorY() {
        return cursorY;
    }

    public boolean cursorVisible() {
        return cursorVisible;
    }

    @Override
    public void save(DataCompound out) {
        out.putInt("mode", current);
        out.putByteArray("vram", vram);
        out.putIntArray("palette", palette);
        out.putInt("cursorX", cursorX);
        out.putInt("cursorY", cursorY);
        out.putBoolean("cursorVisible", cursorVisible);
        if (current >= 0) {
            VideoMode m = mode();
            out.putInt("format", m.format().code());
            out.putInt("width", m.width());
            out.putInt("height", m.height());
        }
    }

    @Override
    public void load(DataCompound in) {
        int index = -1;
        for (int i = 0; i < modes.size(); i++) {
            VideoMode m = modes.get(i);
            if (m.format().code() == in.getInt("format")
                && m.width() == in.getInt("width")
                && m.height() == in.getInt("height")) index = i;
        }
        byte[] bytes = in.getByteArray("vram");
        int[] colors = in.getIntArray("palette");
        if (colors == null || colors.length != 256 || bytes == null)
            throw new IllegalArgumentException("Invalid video state");
        if (index >= 0 && bytes.length != modes.get(index).bytes())
            throw new IllegalArgumentException("Invalid VRAM length");
        reset(index);
        if (index >= 0) System.arraycopy(bytes, 0, vram, 0, bytes.length);
        System.arraycopy(colors, 0, palette, 0, 256);
        cursorX = in.getInt("cursorX");
        cursorY = in.getInt("cursorY");
        cursorVisible = in.getBoolean("cursorVisible") && index >= 0;
        if (index >= 0
            && (cursorX < 0
            || cursorY < 0
            || cursorX >= mode().width()
            || cursorY >= mode().height()))
            throw new IllegalArgumentException("Invalid cursor");
    }

    public record Update(int offset, byte[] bytes) {
        public Update {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /**
     * Immutable transport shared by standalone and Minecraft renderers. Capture at a safe boundary.
     */
    public record DisplayState(long generation, VideoMode mode, int[] palette, List<Update> updates,
                               int cursorX, int cursorY, boolean cursorVisible, boolean monochrome) {
        public DisplayState {
            palette = palette.clone();
            updates = List.copyOf(updates);
        }

        @Override
        public int[] palette() {
            return palette.clone();
        }
    }
}
