package jp.akkiserver.rc.emulator;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Original CP437 VGA glyphs, keyed by Unicode so the RC text ABI stays unchanged.
 */
final class VgaFont {
    static final int WIDTH = 8, HEIGHT = 16;
    private static final Map<Character, byte[]> GLYPHS = load();

    private VgaFont() {
    }

    private static Map<Character, byte[]> load() {
        String resource = "fonts/vga-cp437-8x16.hex";
        try (InputStream stream =
                 Objects.requireNonNull(
                     VgaFont.class.getResourceAsStream(resource), resource);
             BufferedReader reader =
                 new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Map<Character, byte[]> glyphs = new HashMap<>();
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("#") || line.isBlank())
                    continue;

                String[] parts = line.split(":", -1);
                byte[] pixels = HexFormat.of().parseHex(parts[1]);
                if (pixels.length != HEIGHT)
                    throw new IOException("Invalid VGA glyph height");
                glyphs.put((char) Integer.parseInt(parts[0], 16), pixels);
            }
            if (glyphs.size() != 256)
                throw new IOException("Incomplete CP437 font");

            return Map.copyOf(glyphs);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    /**
     * Only draws set bits; the caller paints each cell's background first.
     */
    static boolean draw(BufferedImage image, int x, int y, char character, int color) {
        byte[] glyph = GLYPHS.get(character);
        if (glyph == null)
            return false;

        for (int row = 0; row < HEIGHT; row++)
            for (int column = 0; column < WIDTH; column++)
                if ((glyph[row] & (0x80 >>> column)) != 0)
                    image.setRGB(x + column, y + row, color);

        return true;
    }
}
