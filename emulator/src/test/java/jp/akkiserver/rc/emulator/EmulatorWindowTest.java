package jp.akkiserver.rc.emulator;

import jp.akkiserver.rc.core.machine.Machine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorWindowTest {
    @Test
    void opensPaintsAndClosesTheActualWindow() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        var controller = new EmulatorController(null);
        JFrame[] window = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(
                () -> {
                    EmulatorMain.window(controller, null);
                    for (Frame frame : Frame.getFrames())
                        if (frame instanceof JFrame f
                            && f.getTitle().equals("Retro Computings — Standalone"))
                            window[0] = f;
                    assertNotNull(window[0]);
                    assertTrue(window[0].isShowing());
                });
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (controller.machine.state() != Machine.State.WAITING
                && System.nanoTime() < deadline) Thread.sleep(25);
            assertNull(controller.machine.failure());
            assertEquals(
                Machine.State.WAITING,
                controller.machine.state());
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        BufferedImage image =
                            new BufferedImage(
                                window[0].getWidth(),
                                window[0].getHeight(),
                                BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        window[0].printAll(graphics);
                        graphics.dispose();
                        Path output = Path.of("build/window-smoke.png");
                        Files.createDirectories(output.getParent());
                        ImageIO.write(image, "png", output.toFile());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    window[0].dispatchEvent(
                        new WindowEvent(window[0], WindowEvent.WINDOW_CLOSING));
                });
            while (window[0].isDisplayable() && System.nanoTime() < deadline)
                Thread.sleep(25);
            assertFalse(window[0].isDisplayable());
        } finally {
            controller.requestClose();
        }
    }
}
