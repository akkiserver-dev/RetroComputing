package jp.akkiserver.rc.core.runtime;

import jp.akkiserver.rc.api.runtime.DeviceException;
import jp.akkiserver.rc.api.runtime.StdErrors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Executes Java methods without knowing about guest memory, threads or a VM.
 */
public final class MethodInvoker {
    private final Consumer<Exception> exceptionHandler;

    public MethodInvoker() {
        this(ignored -> {
        });
    }

    public MethodInvoker(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    }

    public CallResult invokeEncoded(MethodTable table, int id, byte[] data) {
        if (!table.contains(id)) return CallResult.failure(StdErrors.E_NOMETHOD);
        Object[] args;
        try {
            args = MethodCodec.decodeArguments(table.javaMethod(id), data);
        } catch (IllegalArgumentException e) {
            return CallResult.failure(StdErrors.E_INVAL);
        }
        return invoke(table, id, args);
    }

    public CallResult invoke(MethodTable table, int id, Object[] args) {
        if (!table.contains(id)) return CallResult.failure(StdErrors.E_NOMETHOD);
        Method method = table.javaMethod(id);
        try {
            MethodCodec.validateArguments(method.getParameterTypes(), args);
        } catch (IllegalArgumentException | NullPointerException e) {
            return CallResult.failure(StdErrors.E_INVAL);
        }
        try {
            Object value = method.invoke(table.target(), args);
            return CallResult.success(method.getReturnType(), value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof DeviceException error) return CallResult.failure(error.code());
            report(cause instanceof Exception error ? error : new RuntimeException(cause));
            return CallResult.failure(StdErrors.E_INTERNAL);
        } catch (Exception e) {
            report(e);
            return CallResult.failure(StdErrors.E_INTERNAL);
        }
    }

    private void report(Exception exception) {
        try {
            exceptionHandler.accept(exception);
        } catch (Exception ignored) {
            /* Logging must not change a device result. */
        }
    }
}
