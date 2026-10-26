package jp.akkiserver.rc.emulator;

import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.api.video.VideoMode;
import jp.akkiserver.rc.core.device.VideoDevice;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Frontend-owned VRAM. Never reads mutable device state on the Swing event thread.
 */
final class DisplayBuffer {
    static final int CELL_WIDTH = VgaFont.WIDTH, CELL_HEIGHT = VgaFont.HEIGHT;
    private long generation = Long.MIN_VALUE;
    private VideoMode mode;
    private byte[] memory = new byte[0];
    private int[] palette = new int[256];
    private int cursorX, cursorY;
    private boolean cursorVisible, monochrome;

    void accept(VideoDevice.DisplayState state) {
        if (generation != state.generation() || !Objects.equals(mode, state.mode())) {
            generation = state.generation();
            mode = state.mode();
            memory = mode == null ? new byte[0] : new byte[mode.bytes()];
        }

        for (VideoDevice.Update update : state.updates()) {
            byte[] bytes = update.bytes();
            if (update.offset() < 0 || (long) update.offset() + bytes.length > memory.length)
                throw new IllegalArgumentException("Invalid display delta");
            System.arraycopy(bytes, 0, memory, update.offset(), bytes.length);
        }

        palette = state.palette();
        cursorX = state.cursorX();
        cursorY = state.cursorY();
        cursorVisible = state.cursorVisible();
        monochrome = state.monochrome();
    }

    boolean ready() {
        return mode != null;
    }

    BufferedImage render() {
        if (mode == null) {
            BufferedImage image = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(0x182022));
            g.fillRect(0, 0, 800, 500);
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
            g.drawString("NO SIGNAL", 340, 250);
            g.dispose();
            return image;
        }
        return mode.format().isText() ? text() : pixels();
    }

    private int color(int rgb) {
        if (!monochrome)
            return rgb & 0xffffff;
        int gray = (((rgb >>> 16) & 255) * 54 + ((rgb >>> 8) & 255) * 183 + (rgb & 255) * 19) >>> 8;
        return (gray / 4) << 16 | gray << 8 | gray / 3;
    }

    private BufferedImage pixels() {
        BufferedImage image =
            new BufferedImage(mode.width(), mode.height(), BufferedImage.TYPE_INT_RGB);
        int bits = mode.format().bits(), stride = mode.format().rowBytes(mode.width());
        for (int y = 0; y < mode.height(); y++)
            for (int x = 0; x < mode.width(); x++) {
                int offset = y * stride + x * bits / 8, rgb;
                if (bits == 16) {
                    int value =
                        Byte.toUnsignedInt(memory[offset])
                            | Byte.toUnsignedInt(memory[offset + 1]) << 8;
                    rgb =
                        ((value >>> 11) * 255 / 31) << 16
                            | (((value >>> 5) & 63) * 255 / 63) << 8
                            | (value & 31) * 255 / 31;
                } else {
                    int index =
                        bits == 8
                            ? Byte.toUnsignedInt(memory[offset])
                            : (memory[offset] >>> (8 - bits - x * bits % 8))
                            & ((1 << bits) - 1);
                    rgb = palette[index];
                }
                image.setRGB(x, y, color(rgb));
            }

        return image;
    }

    private BufferedImage text() {
        BufferedImage image =
            new BufferedImage(
                mode.width() * CELL_WIDTH,
                mode.height() * CELL_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        int cell = mode.format() == PixelFormat.TEXT16 ? 3 : 4;

        for (int y = 0; y < mode.height(); y++)
            for (int x = 0; x < mode.width(); x++) {
                int offset = (y * mode.width() + x) * cell,
                    attr = Byte.toUnsignedInt(memory[offset + 2]);
                int background = cell == 3 ? attr >>> 4 : Byte.toUnsignedInt(memory[offset + 3]);
                g.setColor(new Color(color(palette[background])));
                g.fillRect(x * CELL_WIDTH, y * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT);
            }

        for (int y = 0; y < mode.height(); y++)
            for (int x = 0; x < mode.width(); x++) {
                int offset = (y * mode.width() + x) * cell;
                char character =
                    (char)
                        (Byte.toUnsignedInt(memory[offset])
                            | Byte.toUnsignedInt(memory[offset + 1]) << 8);
                if (character == 0 || Character.isISOControl(character))
                    continue;
                int foreground = Byte.toUnsignedInt(memory[offset + 2]);
                if (cell == 3)
                    foreground &= 15;
                int ink = color(palette[foreground]);
                if (!VgaFont.draw(image, x * CELL_WIDTH, y * CELL_HEIGHT, character, ink)) {
                    // Keep non-CP437 text (e.g. Japanese) available through the host font.
                    g.setColor(new Color(ink));
                    g.drawString(String.valueOf(character), x * CELL_WIDTH, y * CELL_HEIGHT + 13);
                }
            }

        if (cursorVisible
            && cursorX >= 0
            && cursorY >= 0
            && cursorX < mode.width()
            && cursorY < mode.height()) {
            g.setColor(new Color(color(0xffffff)));
            g.fillRect(
                cursorX * CELL_WIDTH, cursorY * CELL_HEIGHT + CELL_HEIGHT - 2, CELL_WIDTH, 2);
        }
        g.dispose();
        return image;
    }
}
