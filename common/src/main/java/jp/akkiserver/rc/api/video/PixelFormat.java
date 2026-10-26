package jp.akkiserver.rc.api.video;

/**
 * Pixel and text encodings supported by video modes.
 *
 * @author akki697222
 */
public enum PixelFormat {
    /**
     * 24-bit text cells.
     */
    TEXT16(1, 24, true),

    /**
     * 32-bit text cells.
     */
    TEXT256(2, 32, true),

    /**
     * One-bit indexed pixels.
     */
    IDX1(0x11, 1, false),

    /**
     * Two-bit indexed pixels.
     */
    IDX2(0x12, 2, false),

    /**
     * Four-bit indexed pixels.
     */
    IDX4(0x14, 4, false),

    /**
     * Eight-bit indexed pixels.
     */
    IDX8(0x18, 8, false),

    /**
     * Sixteen-bit RGB 5-6-5 pixels.
     */
    RGB565(0x30, 16, false);

    private final int code, bits;
    private final boolean text;

    PixelFormat(int code, int bits, boolean text) {
        this.code = code;
        this.bits = bits;
        this.text = text;
    }

    /**
     * Returns the format code used by the API.
     *
     * @return format code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the number of bits per pixel or text cell.
     *
     * @return bits per pixel or text cell
     */
    public int bits() {
        return bits;
    }

    /**
     * Returns whether this format represents text cells.
     *
     * @return whether this is a text format
     */
    public boolean text() {
        return text;
    }

    /**
     * Returns whether this format represents text cells.
     *
     * @return whether this is a text format
     */
    public boolean isText() {
        return text;
    }

    /**
     * Computes the storage size for a frame with the given dimensions.
     *
     * @param width frame width
     * @param height frame height
     * @return required bytes
     * @throws IllegalArgumentException if either dimension is negative
     * @throws ArithmeticException if the result overflows
     */
    public long bytesFor(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        return Math.multiplyExact(((long) width * bits + 7) / 8, height);
    }

    /**
     * Computes the storage size of one row at the given width.
     *
     * @param width row width
     * @return required bytes
     * @throws ArithmeticException if the result does not fit an {@code int}
     */
    public int rowBytes(int width) {
        return Math.toIntExact(((long) width * bits + 7) / 8);
    }
}
