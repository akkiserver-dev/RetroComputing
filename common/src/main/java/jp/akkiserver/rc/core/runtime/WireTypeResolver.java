package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.Unsigned;

import javax.annotation.Nonnull;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Decides the {@link WireType} of a Java type.
 * <p>
 * Mapping (the second column applies when the type is marked {@link Unsigned}):
 * <pre>
 * Java                    signed   unsigned
 * boolean                 bool     (not allowed)
 * byte                    i8       u8
 * short                   i16      u16
 * char                    u16      (not allowed, already unsigned)
 * int                     i32      u32
 * long                    i64      u64
 * float / double          f32/f64  (not allowed)
 * String                  string   (not allowed)
 * void (return only)      void     (not allowed)
 * T[] of any of the above T[]      with the element unsigned
 * </pre>
 * Anything else, including boxed types and nested arrays, is rejected.
 *
 * @since 0.1.0
 */
public final class WireTypeResolver {

    private WireTypeResolver() {
    }

    /**
     * Resolves a Java class.
     *
     * @param type     the Java type; {@code void} is accepted for a return type
     * @param unsigned whether the type is marked {@link Unsigned}
     * @throws IllegalArgumentException if the type has no wire type, or cannot be unsigned
     */
    public static WireType of(Class<?> type, boolean unsigned) {
        boolean array = type.isArray();
        Class<?> element = array ? type.getComponentType() : type;
        if (element.isArray()) {
            throw new IllegalArgumentException("nested arrays are not supported: " + type.getTypeName());
        }
        if (unsigned && element == char.class) {
            throw new IllegalArgumentException("@Unsigned cannot be used on char");
        }
        WireType.Kind kind = WireType.Kind.fromClass(element);
        return new WireType(unsigned ? kind.unsigned() : kind, array);
    }

    /**
     * Resolves a method parameter, reading {@link Unsigned} from the parameter
     * or from its type (including an array's element type).
     *
     * @throws IllegalArgumentException if the parameter has no wire type
     */
    public static WireType forParameter(Parameter parameter) {
        boolean unsigned = parameter.isAnnotationPresent(Unsigned.class)
                || isUnsigned(parameter.getAnnotatedType());
        return of(parameter.getType(), unsigned);
    }

    /**
     * Resolves a method's return type, reading {@link Unsigned} from the
     * method or from its return type.
     *
     * @throws IllegalArgumentException if the return type has no wire type
     */
    public static WireType forReturn(Method method) {
        boolean unsigned = method.isAnnotationPresent(Unsigned.class)
                || isUnsigned(method.getAnnotatedReturnType());
        return of(method.getReturnType(), unsigned);
    }

    /**
     * Resolves a whole method. The result lists the parameters in order.
     *
     * @throws IllegalArgumentException if any parameter or the return type has no wire type
     */
    public static Signature forMethod(Method method) {
        Parameter[] parameters = method.getParameters();
        java.util.List<WireType> params = new java.util.ArrayList<>(parameters.length);
        for (Parameter p : parameters) {
            params.add(forParameter(p));
        }
        return new Signature(params, forReturn(method));
    }

    private static boolean isUnsigned(AnnotatedType type) {
        if (type.isAnnotationPresent(Unsigned.class)) {
            return true;
        }
        if (type instanceof AnnotatedArrayType array) {
            return isUnsigned(array.getAnnotatedGenericComponentType());
        }
        return false;
    }
}
