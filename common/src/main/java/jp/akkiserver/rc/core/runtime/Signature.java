package jp.akkiserver.rc.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The argument and return types of a device method.
 * <p>
 * Text form: the argument types separated by spaces, then {@code ->} and the
 * return type. A method that returns nothing says {@code void}; the arrow is
 * never left out.
 * <pre>
 * u16 u16 string u16 -> void
 * u32 u8[] -> i32
 * -> u16
 * </pre>
 *
 * @param params the argument types in order; none may be {@code void}
 * @param ret    the return type, {@link WireType#VOID} for none
 * @since 0.1.0
 */
public record Signature(List<WireType> params, WireType ret) {

    /**
     * The most arguments a method may have (the count is one byte in {@code dev_describe}).
     */
    public static final int MAX_PARAMS = 255;

    private static final String ARROW = "->";

    /**
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if there are more than {@link #MAX_PARAMS} arguments,
     *                                  or an argument is {@code void}
     */
    public Signature {
        params = List.copyOf(params);
        Objects.requireNonNull(ret, "ret");
        if (params.size() > MAX_PARAMS) {
            throw new IllegalArgumentException("too many arguments: " + params.size() + " (max " + MAX_PARAMS + ")");
        }
        for (WireType p : params) {
            if (p.kind() == WireType.Kind.VOID) {
                throw new IllegalArgumentException("void is only valid as a return type");
            }
        }
    }

    /**
     * Parses the text form. Whitespace around the arrow is optional.
     *
     * @throws IllegalArgumentException if the text is not a valid signature
     */
    public static Signature parse(String text) {
        Objects.requireNonNull(text, "text");
        int arrow = text.indexOf(ARROW);
        if (arrow < 0) {
            throw new IllegalArgumentException("missing \"->\" in signature: \"" + text + "\"");
        }
        if (text.indexOf(ARROW, arrow + ARROW.length()) >= 0) {
            throw new IllegalArgumentException("more than one \"->\" in signature: \"" + text + "\"");
        }
        String left = text.substring(0, arrow).strip();
        String right = text.substring(arrow + ARROW.length()).strip();
        if (right.isEmpty()) {
            throw new IllegalArgumentException("missing return type, use \"void\" for none: \"" + text + "\"");
        }
        if (right.matches(".*\\s.*")) {
            throw new IllegalArgumentException("more than one return type: \"" + text + "\"");
        }
        List<WireType> params = new ArrayList<>();
        if (!left.isEmpty()) {
            for (String token : left.split("\\s+")) {
                params.add(WireType.parse(token));
            }
        }
        return new Signature(params, WireType.parse(right));
    }

    /**
     * The text form, such as {@code "u16 u16 -> void"}.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (WireType p : params) {
            sb.append(p).append(' ');
        }
        return sb.append(ARROW).append(' ').append(ret).toString();
    }
}
