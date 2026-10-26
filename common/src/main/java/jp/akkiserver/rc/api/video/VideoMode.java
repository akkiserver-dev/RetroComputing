package jp.akkiserver.rc.api.video;

import java.util.Objects;

/**
 * Describes a video mode supported by a display card.
 *
 * @param id mode identifier in the range accepted by the API
 * @param format pixel or text representation used by the mode
 * @param width mode width in units interpreted by {@code format}
 * @param height mode height in units interpreted by {@code format}
 * @author akki697222
 */
public record VideoMode(int id, PixelFormat format, int width, int height) {
    /**
     * Validates the supplied mode fields and frame size.
     *
     * @param id mode identifier
     * @param format pixel or text representation used by the mode
     * @param width mode width in units interpreted by {@code format}
     * @param height mode height in units interpreted by {@code format}
     * @throws NullPointerException if {@code format} is {@code null}
     * @throws IllegalArgumentException if an identifier or dimension is out of range
     * @throws ArithmeticException if the mode's frame size overflows
     */
    public VideoMode {
        Objects.requireNonNull(format);
        if (id < 0 || id >= 65535 || width < 1 || width > 65535 || height < 1 || height > 65535)
            throw new IllegalArgumentException();
        Math.multiplyExact(format.rowBytes(width), height);
    }

    /**
     * Returns the number of bytes required for one frame at this mode.
     *
     * @return frame size in bytes
     */
    public int bytes() {
        return format.rowBytes(width) * height;
    }
}
