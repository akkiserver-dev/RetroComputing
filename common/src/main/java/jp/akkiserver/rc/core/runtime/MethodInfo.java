package jp.akkiserver.rc.core.runtime;

import javax.annotation.Nonnull;
import java.util.Objects;

public record MethodInfo(int id, @Nonnull String name, @Nonnull Signature signature, boolean async) {
    public MethodInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(signature, "signature");
    }

    public MethodInfo(int id, @Nonnull String name, @Nonnull Signature signature) {
        this(id, name, signature, false);
    }
}
