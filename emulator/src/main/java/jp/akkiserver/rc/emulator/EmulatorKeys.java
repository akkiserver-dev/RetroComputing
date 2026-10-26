package jp.akkiserver.rc.emulator;

import java.awt.event.KeyEvent;

/**
 * AWT to the GLFW codes used by RC's keyboard ABI.
 */
final class EmulatorKeys {
    private EmulatorKeys() {
    }

    static int modifiers(KeyEvent event) {
        return (event.isShiftDown() ? 1 : 0)
            | (event.isControlDown() ? 2 : 0)
            | (event.isAltDown() ? 4 : 0)
            | (event.isMetaDown() ? 8 : 0);
    }

    static int key(KeyEvent event) {
        int code = event.getKeyCode();
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z
            || code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)
            return code;
        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12)
            return 290 + code - KeyEvent.VK_F1;
        if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9)
            return 320 + code - KeyEvent.VK_NUMPAD0;

        return switch (code) {
            case KeyEvent.VK_SPACE -> 32;
            case KeyEvent.VK_QUOTE -> 39;
            case KeyEvent.VK_COMMA -> 44;
            case KeyEvent.VK_MINUS -> 45;
            case KeyEvent.VK_PERIOD -> 46;
            case KeyEvent.VK_SLASH -> 47;
            case KeyEvent.VK_SEMICOLON -> 59;
            case KeyEvent.VK_EQUALS -> 61;
            case KeyEvent.VK_OPEN_BRACKET -> 91;
            case KeyEvent.VK_BACK_SLASH -> 92;
            case KeyEvent.VK_CLOSE_BRACKET -> 93;
            case KeyEvent.VK_BACK_QUOTE -> 96;
            case KeyEvent.VK_ESCAPE -> 256;
            case KeyEvent.VK_ENTER -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD ? 335 : 257;
            case KeyEvent.VK_TAB -> 258;
            case KeyEvent.VK_BACK_SPACE -> 259;
            case KeyEvent.VK_INSERT -> 260;
            case KeyEvent.VK_DELETE -> 261;
            case KeyEvent.VK_RIGHT -> 262;
            case KeyEvent.VK_LEFT -> 263;
            case KeyEvent.VK_DOWN -> 264;
            case KeyEvent.VK_UP -> 265;
            case KeyEvent.VK_PAGE_UP -> 266;
            case KeyEvent.VK_PAGE_DOWN -> 267;
            case KeyEvent.VK_HOME -> 268;
            case KeyEvent.VK_END -> 269;
            case KeyEvent.VK_CAPS_LOCK -> 280;
            case KeyEvent.VK_SCROLL_LOCK -> 281;
            case KeyEvent.VK_NUM_LOCK -> 282;
            case KeyEvent.VK_PRINTSCREEN -> 283;
            case KeyEvent.VK_PAUSE -> 284;
            case KeyEvent.VK_DECIMAL -> 330;
            case KeyEvent.VK_DIVIDE -> 331;
            case KeyEvent.VK_MULTIPLY -> 332;
            case KeyEvent.VK_SUBTRACT -> 333;
            case KeyEvent.VK_ADD -> 334;
            case KeyEvent.VK_SHIFT -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 344 : 340;
            case KeyEvent.VK_CONTROL -> event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 345 : 341;
            case KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH ->
                event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 346 : 342;
            case KeyEvent.VK_META, KeyEvent.VK_WINDOWS ->
                event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT ? 347 : 343;
            case KeyEvent.VK_CONTEXT_MENU -> 348;
            default -> -1;
        };
    }
}
