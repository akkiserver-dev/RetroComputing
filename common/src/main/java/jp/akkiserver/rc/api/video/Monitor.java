package jp.akkiserver.rc.api.video;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Describes display capabilities; rendering belongs to the Minecraft or emulator client.
 *
 * @author akki697222
 */
public interface Monitor {
    /**
     * Creates a monitor with the supplied text and pixel resolutions.
     *
     * @param mono whether the monitor is monochrome
     * @param text supported text resolutions
     * @param pixel supported pixel resolutions
     * @return a monitor that accepts only the listed resolutions
     */
    static Monitor fixed(boolean mono, List<Size> text, List<Size> pixel) {
        Set<Resolution> sizes = new HashSet<>();
        for (Size size : text) sizes.add(new Resolution(true, size.width(), size.height()));
        for (Size size : pixel) sizes.add(new Resolution(false, size.width(), size.height()));
        return fixed(sizes, mono);
    }

    /**
     * Creates a monitor supporting resolutions up to the supplied text and pixel sizes.
     *
     * @param mono whether the monitor is monochrome
     * @param text maximum text resolution
     * @param pixel maximum pixel resolution
     * @return a monitor that accepts positive dimensions within those maxima
     * @throws IllegalArgumentException if either size has a dimension less than one
     */
    static Monitor multisync(boolean mono, Size text, Size pixel) {
        return multisync(text.width(), text.height(), pixel.width(), pixel.height(), mono);
    }

    /**
     * Creates a monitor accepting exactly the supplied resolutions.
     *
     * @param resolutions resolutions accepted by the monitor
     * @param monochrome whether the monitor is monochrome
     * @return a monitor configured with the supplied capabilities
     */
    static Monitor fixed(Set<Resolution> resolutions, boolean monochrome) {
        Set<Resolution> copy = Set.copyOf(resolutions);
        return new Monitor() {
            public boolean accepts(boolean text, int width, int height) {
                return copy.contains(new Resolution(text, width, height));
            }

            public boolean monochrome() {
                return monochrome;
            }
        };
    }

    /**
     * Creates a monitor accepting positive resolutions up to the supplied maxima.
     *
     * @param textWidth maximum text width
     * @param textHeight maximum text height
     * @param pixelWidth maximum pixel width
     * @param pixelHeight maximum pixel height
     * @param monochrome whether the monitor is monochrome
     * @return a monitor configured with the supplied limits
     * @throws IllegalArgumentException if any maximum is less than one
     */
    static Monitor multisync(
        int textWidth, int textHeight, int pixelWidth, int pixelHeight, boolean monochrome) {
        if (textWidth < 1 || textHeight < 1 || pixelWidth < 1 || pixelHeight < 1)
            throw new IllegalArgumentException();
        return new Monitor() {
            public boolean accepts(boolean text, int width, int height) {
                return width > 0
                    && height > 0
                    && width <= (text ? textWidth : pixelWidth)
                    && height <= (text ? textHeight : pixelHeight);
            }

            public boolean monochrome() {
                return monochrome;
            }
        };
    }

    /**
     * Reports whether this monitor accepts the requested dimensions.
     *
     * @param text whether the requested mode is text-based
     * @param width requested width
     * @param height requested height
     * @return whether the dimensions are accepted
     */
    boolean accepts(boolean text, int width, int height);

    /**
     * Reports whether this monitor is monochrome.
     *
     * @return whether the monitor is monochrome
     */
    boolean monochrome();

    /**
     * A resolution paired with its text or pixel mode kind.
     *
     * @param text whether the resolution is for text mode
     * @param width horizontal size
     * @param height vertical size
     * @author akki697222
     */
    record Resolution(boolean text, int width, int height) {
        /**
         * Validates that both dimensions are positive.
         *
         * @param text whether this is a text resolution
         * @param width horizontal size
         * @param height vertical size
         * @throws IllegalArgumentException if either dimension is less than one
         */
        public Resolution {
            if (width < 1 || height < 1) throw new IllegalArgumentException();
        }
    }
}
