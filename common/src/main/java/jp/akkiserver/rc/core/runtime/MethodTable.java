package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.Export;
import jp.akkiserver.rc.api.runtime.Module;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class MethodTable {
    private static final int MAX_EXPORTS = 0xFFFF;
    private static final ClassValue<Layout> LAYOUTS = new ClassValue<>() {
        @Override
        protected Layout computeValue(@Nonnull Class<?> type) {
            validateExports(type);
            List<Method> exports = collectExports(type);
            validateNames(exports);
            for (Method method : exports) {
                if (!method.trySetAccessible()) throw new IllegalArgumentException("Inaccessible export: " + method);
            }
            return new Layout(List.copyOf(exports), List.copyOf(createInfos(exports)));
        }
    };

    private final Object target;
    private final List<Method> exports;
    private final List<MethodInfo> methods;

    private MethodTable(Object target, List<Method> exports, List<MethodInfo> methods) {
        this.target = target;
        this.exports = List.copyOf(exports);
        this.methods = List.copyOf(methods);
    }

    public static MethodTable create(@Nonnull Object target) {
        Objects.requireNonNull(target);

        if (target.getClass().getDeclaredAnnotation(Module.class) == null) {
            throw new IllegalArgumentException("target is not a module");
        }

        Layout layout = LAYOUTS.get(target.getClass());
        return new MethodTable(target, layout.exports(), layout.methods());
    }

    private static void validateExports(@Nonnull Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Export.class)) {
                int mod = method.getModifiers();
                if (!Modifier.isPublic(mod)) {
                    throw new IllegalArgumentException("Exported method '" + method.getName() + "' was not public");
                }
                if (Modifier.isStatic(mod)) {
                    throw new IllegalArgumentException("Static method '" + method.getName() + "' was exported");
                }
            }
        }

        Class<?> parent = type.getSuperclass();
        if (parent != null) {
            validateExports(parent);
        }
        for (Class<?> iface : type.getInterfaces()) validateExports(iface);
    }

    private static List<Method> collectExports(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (!method.isSynthetic()
                && !method.isBridge()
                && method.isAnnotationPresent(Export.class)) {
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(Method::getName));
        return methods;
    }

    private static void validateNames(List<Method> methods) {
        String previous = null;
        for (Method method : methods) {
            String name = method.getName();
            if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")
                || name.length() > 255
                || name.equals(previous)) {
                throw new IllegalArgumentException("Invalid or duplicate exported method name: " + name);
            }
            previous = name;
        }
    }

    private static List<MethodInfo> createInfos(List<Method> exports) {
        if (exports.size() > MAX_EXPORTS) {
            throw new IllegalArgumentException("Too many exported methods: " + exports.size());
        }

        List<MethodInfo> methods = new ArrayList<>();

        for (int i = 0; i < exports.size(); i++) {
            Method method = exports.get(i);

            methods.add(new MethodInfo(
                i,
                method.getName(),
                WireTypeResolver.forMethod(method),
                method.getAnnotation(Export.class).async()
            ));
        }
        return methods;
    }

    public List<MethodInfo> methods() {
        return methods;
    }

    public boolean contains(int id) {
        return id >= 0 && id < methods.size();
    }

    public MethodInfo method(int id) {
        return methods.get(id);
    }

    public Method javaMethod(int id) {
        return exports.get(id);
    }

    Object target() {
        return target;
    }

    private record Layout(List<Method> exports, List<MethodInfo> methods) {
    }
}
