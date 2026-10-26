package jp.akkiserver.rc.emulator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class EmulatorMain {
    private EmulatorMain() {
    }

    public static void main(String[] args) throws Exception {
        Path boot = null, screenshot = null;
        String script = null;
        int ticks = 200;

        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--boot" -> boot = Path.of(args[++i]);
                case "--screenshot" -> screenshot = Path.of(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--type" -> script = args[++i];
                case "--typefile" -> script = Files.lines(Path.of(args[++i]))
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .collect(Collectors.joining("|"));
                case "--help" -> {
                    System.out.println(
                        "RC emulator: [--boot OS.wasm] [--screenshot screen.png --ticks 200]"
                            + " [--type \"cmd1|cmd2\" | --typefile script.txt types"
                            + " the script into the guest (newline = Enter)]\n"
                            + "Without --boot, runs the built-in Wasm demo. Slots: EEPROM=0"
                            + " video=1 keyboard=2 volatile disk=3.");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        if (ticks < 1)
            throw new IllegalArgumentException("ticks must be positive");

        if (screenshot != null) {
            System.setProperty("java.awt.headless", "true");
            try (EmulatorController controller = new EmulatorController(boot)) {
                AtomicReference<BufferedImage> frame = new AtomicReference<>();
                String[] lines = script == null ? new String[0] : script.split("\\|");
                int lineAt = 0, charAt = 0, armAt = -1;
                for (int i = 0; i < ticks; i++) {
                    controller.tick(frame::set);
                    Thread.sleep(10);
                    for (int j = 0; j < 100 && !controller.logs.isEmpty(); j++) {
                        String log = controller.logs.poll();
                        System.err.println(log);
                        if (log.contains("entering shell") && armAt < 0)
                            armAt = i + 50;
                    }
                    if (armAt >= 0 && i >= armAt && lineAt < lines.length && i % 2 == 0) {
                        String line = lines[lineAt];
                        if (charAt < line.length()) {
                            controller.keyboard.key(3, 0, 0, line.charAt(charAt));
                            charAt++;
                        } else {
                            controller.keyboard.key(1, 0, 257, 0);
                            controller.keyboard.key(2, 0, 257, 0);
                            charAt = 0;
                            lineAt++;
                            armAt = i + 30;
                        }
                    }
                }
                if (controller.machine.failure() != null) {
                    controller.logs.forEach(System.err::println);
                    throw new IllegalStateException("Guest failed", controller.machine.failure());
                }

                if (frame.get() == null || !controller.display.ready()) {
                    controller.logs.forEach(System.err::println);
                    throw new IllegalStateException("No display frame received");
                }

                Files.createDirectories(screenshot.toAbsolutePath().getParent());
                ImageIO.write(frame.get(), "png", screenshot.toFile());
                System.out.println(controller.status());
                System.out.println("Screenshot: " + screenshot.toAbsolutePath());
                controller.logs.forEach(System.out::println);
            }
            return;
        }

        EmulatorController controller = new EmulatorController(boot);
        Path initial = boot;
        SwingUtilities.invokeLater(() -> window(controller, initial));
    }

    static void window(EmulatorController controller, Path initial) {
        JFrame window = new JFrame("Retro Computings — Standalone");
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        JPanel screen =
            new JPanel() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    BufferedImage frame = image.get();
                    if (frame == null)
                        return;
                    Graphics2D g = (Graphics2D) graphics.create();
                    double scale =
                        Math.min(
                            (double) getWidth() / frame.getWidth(),
                            (double) getHeight() / frame.getHeight());
                    if (scale >= 1)
                        scale = Math.floor(scale);
                    int w = (int) (frame.getWidth() * scale),
                        h = (int) (frame.getHeight() * scale);
                    g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(frame, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                    g.dispose();
                }
            };
        screen.setBackground(Color.BLACK);
        screen.setPreferredSize(
            new Dimension(80 * DisplayBuffer.CELL_WIDTH, 25 * DisplayBuffer.CELL_HEIGHT));
        screen.setFocusable(true);

        JLabel status = new JLabel("Starting...");
        JTextArea logs = new JTextArea(5, 0);
        logs.setEditable(false);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        AtomicReference<Path> file = new AtomicReference<>(initial);
        JButton open = new JButton("Open Wasm"),
            reload = new JButton("Reload"),
            stop = new JButton("Power off");
        toolbar.add(open);
        toolbar.add(reload);
        toolbar.add(stop);
        open.addActionListener(
            e -> {
                JFileChooser picker = new JFileChooser();
                if (picker.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                    file.set(picker.getSelectedFile().toPath());
                    controller.reload(file.get());
                }
                screen.requestFocusInWindow();
            });
        reload.addActionListener(
            e -> {
                controller.reload(file.get());
                screen.requestFocusInWindow();
            });
        stop.addActionListener(e -> controller.stop());

        Set<Integer> held = new HashSet<>();
        screen.addKeyListener(
            new KeyAdapter() {
                private char high;

                @Override
                public void keyPressed(KeyEvent event) {
                    int key = EmulatorKeys.key(event);
                    if (key >= 0) {
                        held.add(key);
                        controller.keyboard.key(1, EmulatorKeys.modifiers(event), key, 0);
                    }
                    event.consume();
                }

                @Override
                public void keyReleased(KeyEvent event) {
                    int key = EmulatorKeys.key(event);
                    if (key >= 0) {
                        held.remove(key);
                        controller.keyboard.key(2, EmulatorKeys.modifiers(event), key, 0);
                    }
                    event.consume();
                }

                @Override
                public void keyTyped(KeyEvent event) {
                    char c = event.getKeyChar();
                    if (Character.isHighSurrogate(c)) {
                        high = c;
                        return;
                    }
                    int cp = c;
                    if (Character.isLowSurrogate(c)) {
                        if (high == 0)
                            return;
                        cp = Character.toCodePoint(high, c);
                    }
                    high = 0;
                    if (!Character.isISOControl(cp))
                        controller.keyboard.key(3, EmulatorKeys.modifiers(event), 0, cp);
                    event.consume();
                }
            });
        screen.setFocusTraversalKeysEnabled(false);

        screen.addFocusListener(
            new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    held.forEach(key -> controller.keyboard.key(2, 0, key, 0));
                    held.clear();
                }
            });
        screen.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    screen.requestFocusInWindow();
                }
            });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(new JScrollPane(logs), BorderLayout.CENTER);
        window.add(toolbar, BorderLayout.NORTH);
        window.add(screen, BorderLayout.CENTER);
        window.add(bottom, BorderLayout.SOUTH);

        ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "rc-emulator-tick"));
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    controller.requestClose();
                    toolbar.setEnabled(false);
                }
            });
        timer.scheduleAtFixedRate(
            () -> {
                try {
                    boolean running = controller.tick(frame -> image.set(frame));
                    String line;
                    StringBuilder messages = new StringBuilder();
                    while ((line = controller.logs.poll()) != null)
                        messages.append(line).append('\n');
                    String text = controller.status();
                    SwingUtilities.invokeLater(
                        () -> {
                            status.setText(text);
                            if (!messages.isEmpty()) {
                                logs.append(messages.toString());
                                if (logs.getDocument().getLength() > 65536)
                                    logs.replaceRange("", 0, 32768);
                                logs.setCaretPosition(logs.getDocument().getLength());
                            }
                            screen.repaint();
                            if (!running)
                                window.dispose();
                        });
                    if (!running)
                        timer.shutdown();
                } catch (Throwable error) {
                    error.printStackTrace();
                    SwingUtilities.invokeLater(() -> status.setText("ERROR: " + error));
                    controller.requestClose();
                }
            },
            0,
            50,
            TimeUnit.MILLISECONDS);

        window.pack();
        window.setLocationByPlatform(true);
        window.setVisible(true);
        screen.requestFocusInWindow();
    }
}
