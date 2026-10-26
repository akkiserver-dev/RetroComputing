package jp.akkiserver.rc.emulator;

import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.api.video.VideoMode;
import jp.akkiserver.rc.core.device.VideoDevice;
import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplayBufferTest {
    private VideoDevice.DisplayState state(
        long generation,
        PixelFormat format,
        int width,
        int height,
        byte[] bytes,
        int[] palette) {
        return new VideoDevice.DisplayState(
            generation,
            new VideoMode(0, format, width, height),
            palette,
            List.of(new VideoDevice.Update(0, bytes)),
            0,
            0,
            false,
            false);
    }

    @Test
    void packedPixelsRespectRowPaddingAndMsbOrder() {
        for (PixelFormat format :
            List.of(PixelFormat.IDX1, PixelFormat.IDX2, PixelFormat.IDX4, PixelFormat.IDX8)) {
            DisplayBuffer buffer = new DisplayBuffer();
            int[] palette = new int[256];
            palette[1] = 0xff00ff;
            int stride = format.rowBytes(3);
            byte[] data = new byte[stride * 2];
            data[0] = (byte) (1 << (8 - format.bits()));
            data[stride] = (byte) (1 << (8 - format.bits()));
            buffer.accept(state(1, format, 3, 2, data, palette));
            var image = buffer.render();
            assertEquals(0xff00ff, image.getRGB(0, 0) & 0xffffff);
            assertEquals(0xff00ff, image.getRGB(0, 1) & 0xffffff);
            assertEquals(0, image.getRGB(1, 0) & 0xffffff);
        }
    }

    @Test
    void rgb565UsesLittleEndianAndFullRange() {
        DisplayBuffer buffer = new DisplayBuffer();
        buffer.accept(
            state(
                1,
                PixelFormat.RGB565,
                3,
                1,
                new byte[]{0, (byte) 0xf8, (byte) 0xe0, 7, 31, 0},
                new int[256]));
        var image = buffer.render();
        assertEquals(0xff0000, image.getRGB(0, 0) & 0xffffff);
        assertEquals(0x00ff00, image.getRGB(1, 0) & 0xffffff);
        assertEquals(0x0000ff, image.getRGB(2, 0) & 0xffffff);
    }

    @Test
    void sameModeResetDropsOldVramWhilePartialUpdatesPreserveIt() {
        DisplayBuffer buffer = new DisplayBuffer();
        int[] palette = new int[256];
        palette[1] = 0xffffff;
        buffer.accept(state(1, PixelFormat.IDX8, 2, 1, new byte[]{1, 1}, palette));
        buffer.accept(state(1, PixelFormat.IDX8, 2, 1, new byte[]{0}, palette));
        assertEquals(0xffffff, buffer.render().getRGB(1, 0) & 0xffffff);
        buffer.accept(state(2, PixelFormat.IDX8, 2, 1, new byte[]{0}, palette));
        assertEquals(0, buffer.render().getRGB(1, 0) & 0xffffff);
    }

    @Test
    void textBackgroundAndCursorRenderForBothCellFormats() {
        for (PixelFormat format : List.of(PixelFormat.TEXT16, PixelFormat.TEXT256)) {
            DisplayBuffer buffer = new DisplayBuffer();
            int[] palette = new int[256];
            palette[1] = 0xaa0000;
            byte[] cell =
                format == PixelFormat.TEXT16
                    ? new byte[]{0, 0, 0x10}
                    : new byte[]{0, 0, 0, 1};
            var state = state(1, format, 1, 1, cell, palette);
            buffer.accept(state);
            assertEquals(0xaa0000, buffer.render().getRGB(0, 0) & 0xffffff);
            buffer.accept(
                new VideoDevice.DisplayState(
                    1, state.mode(), palette, List.of(), 0, 0, true, false));
            assertEquals(
                0xffffff, buffer.render().getRGB(0, DisplayBuffer.CELL_HEIGHT - 1) & 0xffffff);
        }
    }

    @Test
    void captureIsImmutableAndRejectsBadDeltas() {
        int[] palette = new int[256];
        var state = state(1, PixelFormat.IDX8, 1, 1, new byte[]{0}, palette);
        palette[0] = 1;
        assertEquals(0, state.palette()[0]);
        state.palette()[0] = 2;
        assertEquals(0, state.palette()[0]);
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DisplayBuffer()
                    .accept(
                        state(
                            1,
                            PixelFormat.IDX8,
                            1,
                            1,
                            new byte[]{0, 1},
                            palette)));
        assertEquals(800, new DisplayBuffer().render().getWidth());
    }

    @Test
    void keyboardTranslatesNavigationAndRightModifiers() {
        Canvas source = new Canvas();
        assertEquals(
            257,
            EmulatorKeys.key(
                new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n')));
        var right =
            new KeyEvent(
                source,
                KeyEvent.KEY_PRESSED,
                0,
                KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_SHIFT,
                KeyEvent.CHAR_UNDEFINED,
                KeyEvent.KEY_LOCATION_RIGHT);
        assertEquals(344, EmulatorKeys.key(right));
        assertEquals(3, EmulatorKeys.modifiers(right));
    }
}
