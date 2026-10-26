package jp.akkiserver.rc.api.video;

import java.util.List;
import java.util.Set;

/**
 * Describes the memory, bandwidth, formats, and modes of a video card.
 *
 * @param vramBytes available video memory in bytes
 * @param bandwidthPerTick transfer budget per tick
 * @param formats formats supported by the card
 * @param hardwareDraw whether the card supports hardware drawing
 * @param modes supported video modes
 * @author akki697222
 */
public record CardSpec(int vramBytes, int bandwidthPerTick, Set<PixelFormat> formats, boolean hardwareDraw, List<VideoMode> modes) {
    /**
     * Validates and copies the supplied card capabilities.
     *
     * @param vramBytes available video memory in bytes
     * @param bandwidthPerTick transfer budget per tick
     * @param formats formats supported by the card
     * @param hardwareDraw whether the card supports hardware drawing
     * @param modes supported video modes
     * @throws NullPointerException if a collection or one of its elements is {@code null}
     * @throws IllegalArgumentException if memory or bandwidth is below one, or if the mode list is invalid
     */
    public CardSpec {
        if (vramBytes < 1 || bandwidthPerTick < 1) throw new IllegalArgumentException();
        formats = Set.copyOf(formats);
        modes = List.copyOf(modes);
        if (modes.size() > 65535
            || modes.stream().map(VideoMode::id).distinct().count() != modes.size())
            throw new IllegalArgumentException("Invalid mode list");
    }
}
