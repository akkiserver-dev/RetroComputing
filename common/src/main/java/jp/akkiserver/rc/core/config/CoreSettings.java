package jp.akkiserver.rc.core.config;

import jp.akkiserver.rc.core.machine.MachineConfig;
import jp.akkiserver.rc.core.machine.MachineWorkers;
import jp.akkiserver.rc.core.storage.MachineStore;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * The same TOML configuration is usable by Minecraft and standalone hosts.
 */
public record CoreSettings(
    String architecture,
    StorageMode storageMode,
    int workerThreads,
    long workerStackBytes,
    MachineConfig machine) {
    public CoreSettings {
        Objects.requireNonNull(architecture);
        Objects.requireNonNull(storageMode);
        Objects.requireNonNull(machine);
        if (architecture.isBlank()
            || workerThreads < 1
            || workerThreads > 1024
            || workerStackBytes < (1L << 20)
            || workerStackBytes > (1L << 30))
            throw new IllegalArgumentException("Invalid runtime configuration");
    }

    public static CoreSettings defaults() {
        return new CoreSettings(
            "rc:shuttle-wasm32",
            StorageMode.JOURNALED,
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            8L << 20,
            MachineConfig.defaults());
    }

    public static Path defaultRoot() {
        return Path.of("retrocomputing");
    }

    public static CoreSettings load(Path root) throws IOException {
        Files.createDirectories(root);
        Path path = root.resolve("config.toml");
        if (!Files.exists(path)) {
            try {
                Files.writeString(
                    path,
                    defaults().toml(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
            }
        }
        var parsed = Toml.parse(path);
        if (parsed.hasErrors())
            throw new IOException("Invalid RC configuration: " + parsed.errors());
        CoreSettings defaults = defaults();
        try {
            String architecture = parsed.getString("architecture"),
                mode = parsed.getString("storage_mode");
            return new CoreSettings(
                architecture == null ? defaults.architecture() : architecture,
                mode == null
                    ? defaults.storageMode()
                    : StorageMode.valueOf(mode.toUpperCase(Locale.ROOT)),
                integer(parsed, "worker_threads", defaults.workerThreads()),
                number(parsed, "worker_stack_bytes", defaults.workerStackBytes()),
                new MachineConfig(
                    integer(parsed, "machine.slots", defaults.machine().slots()),
                    number(
                        parsed,
                        "machine.budget_per_tick",
                        defaults.machine().budgetPerTick()),
                    integer(
                        parsed,
                        "machine.event_capacity",
                        defaults.machine().eventCapacity()),
                    integer(
                        parsed,
                        "machine.max_tickets",
                        defaults.machine().maxTickets())));
        } catch (RuntimeException error) {
            throw new IOException("Invalid RC configuration value", error);
        }
    }

    private static long number(TomlTable table, String key, long fallback) {
        Long value = table.getLong(key);
        return value == null ? fallback : value;
    }

    private static int integer(TomlTable table, String key, int fallback) {
        return Math.toIntExact(number(table, key, fallback));
    }

    public ExecutorService createWorkers() {
        return MachineWorkers.create(workerThreads, workerStackBytes);
    }

    public MachineStore openStore(Path root, UUID id)
        throws IOException {
        return new MachineStore(
            root, id, storageMode == StorageMode.JOURNALED);
    }

    public String toml() {
        String escaped =
            architecture
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "architecture = \""
            + escaped
            + "\"\nstorage_mode = \""
            + storageMode.name().toLowerCase(Locale.ROOT)
            + "\"\nworker_threads = "
            + workerThreads
            + "\nworker_stack_bytes = "
            + workerStackBytes
            + "\n\n[machine]\nslots = "
            + machine.slots()
            + "\nbudget_per_tick = "
            + machine.budgetPerTick()
            + "\nevent_capacity = "
            + machine.eventCapacity()
            + "\nmax_tickets = "
            + machine.maxTickets()
            + "\n";
    }

    public enum StorageMode {
        DIRECT,
        JOURNALED
    }
}
