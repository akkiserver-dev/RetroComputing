package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.StdErrors;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * A language-independent Java method result. Reference values are copied.
 */
public record CallResult(int status, Class<?> type, Object value) {
    public CallResult {
        Objects.requireNonNull(type, "type");
        if (status > 0 || status < 0 && !StdErrors.isValid(status)) {
            throw new IllegalArgumentException("Invalid call status: " + status);
        }
        if (status == 0 && type != void.class) {
            Objects.requireNonNull(value, "Device methods must not return null");
            MethodCodec.validateValue(type, value);
        }
        if (status < 0 || type == void.class) value = null;
        value = copy(value);
    }

    public static CallResult success(Class<?> type, Object value) {
        return new CallResult(0, type, value);
    }

    public static CallResult failure(int code) {
        return new CallResult(
            StdErrors.isValid(code) ? code : StdErrors.E_INTERNAL, void.class, null);
    }

    private static Object copy(Object value) {
        if (value == null || !value.getClass().isArray()) return value;
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().componentType(), length);
        System.arraycopy(value, 0, copy, 0, length);
        return copy;
    }

    @Override
    public Object value() {
        return copy(value);
    }

    public boolean succeeded() {
        return status == 0;
    }

    public byte[] encoded() {
        if (!succeeded()) throw new IllegalStateException("Failed call has no return value");
        return MethodCodec.encodeReturn(type, value);
    }
}
