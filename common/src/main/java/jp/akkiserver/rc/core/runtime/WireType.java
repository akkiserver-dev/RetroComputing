package jp.akkiserver.rc.core.runtime;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * A type as it appears on the wire between a device method and the guest.
 * <p>
 * A device method's arguments and return value are sent as raw bytes in guest
 * memory. Each one has a {@code WireType}: a {@link Kind} such as {@code u16}
 * or {@code string}, optionally as an array. Java types are converted to wire
 * types by {@link WireTypeResolver}.
 * <p>
 * Text form: the kind keyword, followed by {@code []} for an array
 * ({@code "u16"}, {@code "string[]"}). Numeric form (used by
 * {@code dev_describe}): the kind code, with bit 7 set for an array
 * ({@code u16[]} is {@code 0x85}).
 *
 * @param kind  the element kind
 * @param array whether this is an array of {@code kind}
 * @since 0.1.0
 */
public record WireType(Kind kind, boolean array) {

    /** Marks an array in the numeric form. */
    public static final int ARRAY_FLAG = 0x80;

    /** The return type of a method that returns nothing. */
    public static final WireType VOID = new WireType(Kind.VOID, false);

    /** The kinds of wire types. */
    public enum Kind {
        /** No value. Only valid as a return type, and never an array. */
        VOID(0x00, "void"),
        /** 0 or 1, sent as one byte. */
        BOOL(0x01, "bool"),
        I8(0x02, "i8"),
        U8(0x03, "u8"),
        I16(0x04, "i16"),
        U16(0x05, "u16"),
        I32(0x06, "i32"),
        U32(0x07, "u32"),
        I64(0x08, "i64"),
        U64(0x09, "u64"),
        F32(0x0A, "f32"),
        F64(0x0B, "f64"),
        /** UTF-8 text, sent as a u32 length followed by the bytes. */
        STRING(0x0C, "string");

        private final int code;
        private final String keyword;

        Kind(int code, String keyword) {
            this.code = code;
            this.keyword = keyword;
        }

        /** The numeric code, without the array flag. */
        public int code() {
            return code;
        }

        /** The keyword used in signature strings. */
        public String keyword() {
            return keyword;
        }

        /**
         * Returns the unsigned equivalent of this kind.
         * <p>
         * Signed integer kinds are converted to their unsigned counterparts.
         * Unsigned integer kinds are returned unchanged.
         *
         * @return the corresponding unsigned integer kind
         * @throws IllegalArgumentException if this kind is not an integer kind
         */
        public Kind unsigned() {
            return switch (this) {
                case I8 -> U8;
                case I16 -> U16;
                case I32 -> U32;
                case I64 -> U64;
                case U8, U16, U32, U64 -> this;
                default -> throw new IllegalArgumentException("kind has no unsigned equivalent: " + keyword);
            };
        }

        /**
         * Finds a kind by its numeric code.
         *
         * @throws IllegalArgumentException if no kind has this code
         */
        public static Kind fromCode(int code) {
            for (Kind k : values()) {
                if (k.code == code) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown wire type code: 0x" + Integer.toHexString(code));
        }

        /**
         * Finds a kind by its keyword.
         *
         * @throws IllegalArgumentException if no kind has this keyword
         */
        public static Kind fromKeyword(String keyword) {
            for (Kind k : values()) {
                if (k.keyword.equals(keyword)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown wire type: \"" + keyword + "\"");
        }


        /**
         * Finds a kind by its class.
         *
         * @throws IllegalArgumentException if no kind has this class
         */
        public static Kind fromClass(Class<?> clazz) {
            return switch (clazz) {
                case Class<?> c when c == byte.class -> WireType.Kind.I8;
                case Class<?> c when c == short.class -> WireType.Kind.I16;
                case Class<?> c when c == int.class -> WireType.Kind.I32;
                case Class<?> c when c == long.class -> WireType.Kind.I64;
                case Class<?> c when c == void.class -> WireType.Kind.VOID;
                case Class<?> c when c == boolean.class -> WireType.Kind.BOOL;
                case Class<?> c when c == char.class -> WireType.Kind.U16;
                case Class<?> c when c == float.class -> WireType.Kind.F32;
                case Class<?> c when c == double.class -> WireType.Kind.F64;
                case Class<?> c when c == String.class -> WireType.Kind.STRING;
                default -> throw new IllegalArgumentException("unsupported type: " + clazz.getTypeName());
            };
        }
    }

    /**
     * @throws NullPointerException     if {@code kind} is null
     * @throws IllegalArgumentException if {@code kind} is {@code VOID} and {@code array} is true
     */
    public WireType {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.VOID && array) {
            throw new IllegalArgumentException("void cannot be an array");
        }
    }

    /** A single value of the given kind. */
    public static WireType of(Kind kind) {
        return new WireType(kind, false);
    }

    /** An array of the given kind. */
    public static WireType arrayOf(Kind kind) {
        return new WireType(kind, true);
    }

    /**
     * The numeric code used by {@code dev_describe}.
     *
     * @return the kind code, or-ed with {@link #ARRAY_FLAG} for an array
     */
    public int code() {
        return kind.code() | (array ? ARRAY_FLAG : 0);
    }

    /**
     * Reads a numeric code. The inverse of {@link #code()}.
     *
     * @throws IllegalArgumentException if the code is out of range or unknown
     */
    public static WireType fromCode(int code) {
        if ((code & ~0xFF) != 0) {
            throw new IllegalArgumentException("wire type code out of range: " + code);
        }
        return new WireType(Kind.fromCode(code & ~ARRAY_FLAG), (code & ARRAY_FLAG) != 0);
    }

    /**
     * Parses the text form, such as {@code "u16"} or {@code "string[]"}.
     * Surrounding whitespace is ignored.
     *
     * @throws IllegalArgumentException if the text is not a wire type
     */
    public static WireType parse(String text) {
        String s = Objects.requireNonNull(text, "text").strip();
        boolean array = s.endsWith("[]");
        if (array) {
            s = s.substring(0, s.length() - 2);
        }
        return new WireType(Kind.fromKeyword(s), array);
    }

    /** The text form, such as {@code "u16"} or {@code "string[]"}. */
    @Override
    @Nonnull
    public String toString() {
        return kind.keyword() + (array ? "[]" : "");
    }
}
