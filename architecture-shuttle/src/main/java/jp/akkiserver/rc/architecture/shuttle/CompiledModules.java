package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.shuttle.WasmtimeEngine;
import jp.akkiserver.shuttle.WeaveModule;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Factory-scoped cache shared by machines, with bounded idle entries and explicit ownership.
 */
final class CompiledModules implements AutoCloseable {
    private final WasmtimeEngine engine = createEngine();
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;
    private long bytes;

    private static WasmtimeEngine createEngine() {
        long start = LoadTimings.start();
        WasmtimeEngine engine = new WasmtimeEngine();
        LoadTimings.finish("engine.create", start, "");
        return engine;
    }

    synchronized Lease acquire(byte[] source, boolean child) {
        if (closed)
            throw new IllegalStateException("Architecture factory closed");

        long lookupStart = LoadTimings.start();
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }

        Key key = new Key(hash, child);
        Entry entry = entries.get(key);
        String detail = "kind=" + (child ? "child" : "root") + " hash=" + hash.substring(0, 16)
            + " bytes=" + source.length;
        LoadTimings.finish("cache.lookup", lookupStart,
            detail + " cache=" + (entry == null ? "miss" : "hit"));

        if (entry == null) {
            long validateStart = LoadTimings.start();
            WasmImage.validate(source);
            LoadTimings.finish("image.validate", validateStart, detail);
            WeaveModule.Options.Builder options =
                WeaveModule.Options.builder().pollPeriod(1).stackPages(16);
            if (child)
                options.yieldingImport("sys", "call");
            else
                for (String name : List.of("yield", "hlt", "shutdown", "reboot", "exec"))
                    options.yieldingImport("fw", name);

            long transformStart = LoadTimings.start();
            WeaveModule module = WeaveModule.load(engine, source, options.build());
            LoadTimings.finish("module.transform", transformStart, detail);
            try {
                long compileStart = LoadTimings.start();
                module.validate();
                LoadTimings.finish("module.validate-compile", compileStart, detail);
            } catch (Throwable error) {
                module.close();
                throw error;
            }
            if (!">".equals(module.entries().get("_start"))) {
                module.close();
                throw new IllegalArgumentException("Expected _start() -> void");
            }

            entry = new Entry(module, source.length);
            entries.put(key, entry);
            bytes += source.length;
        }

        Lease lease = new Lease(entry);
        trim();
        return lease;
    }

    private void trim() {
        var iterator = entries.values().iterator();
        while ((entries.size() > 128 || bytes > (32L << 20)) && iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.references != 0)
                continue;
            bytes -= entry.bytes;
            entry.module.close();
            iterator.remove();
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed)
            return;
        if (entries.values().stream().anyMatch(entry -> entry.references != 0))
            throw new IllegalStateException("Machines still use this architecture factory");

        closed = true;
        for (Entry entry : entries.values())
            entry.module.close();
        entries.clear();
        engine.close();
    }

    private record Key(String digest, boolean child) {
    }

    private static final class Entry {
        final WeaveModule module;
        final int bytes;
        int references;

        Entry(WeaveModule module, int bytes) {
            this.module = module;
            this.bytes = bytes;
        }
    }

    final class Lease implements AutoCloseable {
        private Entry entry;

        Lease(Entry entry) {
            this.entry = entry;
            entry.references++;
        }

        WeaveModule module() {
            if (entry == null)
                throw new IllegalStateException("Lease closed");
            return entry.module;
        }

        public void close() {
            synchronized (CompiledModules.this) {
                if (entry != null) {
                    entry.references--;
                    entry = null;
                    trim();
                }
            }
        }
    }
}
