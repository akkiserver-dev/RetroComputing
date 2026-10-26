package jp.akkiserver.rc.emulator;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VgaFontTest {
    @Test
    void asciiUsesTheEightBySixteenVgaBitmapExactly() {
        BufferedImage image = new BufferedImage(8, 16, BufferedImage.TYPE_INT_RGB);
        assertTrue(VgaFont.draw(image, 0, 0, 'A', 0xffffff));
        int[] rows = {0, 0, 0x10, 0x38, 0x6c, 0xc6, 0xc6, 0xfe, 0xc6, 0xc6, 0xc6, 0xc6, 0, 0, 0, 0};
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 8; x++)
                assertEquals(
                    (rows[y] & (0x80 >>> x)) == 0 ? 0 : 0xffffff,
                    image.getRGB(x, y) & 0xffffff);
    }

    @Test
    void cp437GraphicsUseUnicodeAndFillTheWholeCell() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        assertTrue(VgaFont.draw(image, 0, 0, '\u2588', 0xabcdef));
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 8; x++)
                assertEquals(0xabcdef, image.getRGB(x, y) & 0xffffff);
        assertTrue(VgaFont.draw(image, 8, 0, '\u2500', 0xffffff));
        boolean connected = false;
        for (int y = 0; y < 16; y++)
            if ((image.getRGB(8, y) & 0xffffff) == 0xffffff) {
                connected = true;
                assertEquals(0xffffff, image.getRGB(15, y) & 0xffffff);
            }
        assertTrue(connected);
        assertTrue(VgaFont.draw(image, 0, 0, '\u00c7', 0xffffff));
        assertTrue(VgaFont.draw(image, 0, 0, '\u263a', 0xffffff));
        assertFalse(VgaFont.draw(image, 0, 0, '\u0080', 0xffffff));
        assertFalse(VgaFont.draw(image, 0, 0, '\u65e5', 0xffffff));
    }
}
