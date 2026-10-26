package jp.akkiserver.rc.api.video;

/**
 * Width and height pair used to describe a display resolution.
 *
 * @param width horizontal size
 * @param height vertical size
 * @author akki697222
 */
public record Size(int width, int height) {
    /**
     * Validates that both dimensions are positive.
     *
     * @param width horizontal size
     * @param height vertical size
     * @throws IllegalArgumentException if either dimension is less than one
     */
    public Size {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid display size");
    }
}
