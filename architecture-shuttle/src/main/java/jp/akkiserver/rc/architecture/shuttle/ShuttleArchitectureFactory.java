package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureFactory;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;

import java.util.Objects;

public final class ShuttleArchitectureFactory implements ArchitectureFactory, AutoCloseable {
    private final ShuttleConfig config;
    private CompiledModules modules;
    private boolean closed;

    public ShuttleArchitectureFactory() {
        this(ShuttleConfig.defaults());
    }

    public ShuttleArchitectureFactory(ShuttleConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public String id() {
        return "rc:shuttle-wasm32";
    }

    @Override
    public synchronized Architecture create(ArchitectureHost host) {
        if (closed)
            throw new IllegalStateException("Factory closed");
        if (modules == null)
            modules = new CompiledModules();
        return new ShuttleArchitecture(host, config, modules);
    }

    @Override
    public synchronized void close() throws Exception {
        if (modules != null)
            modules.close();
        closed = true;
    }
}
