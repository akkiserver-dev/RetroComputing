package jp.akkiserver.rc.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreSettingsTest {
    @TempDir
    Path root;

    @Test
    void createsDefaultsAndSupportsTomlSyntax() throws Exception {
        assertEquals(CoreSettings.defaults(), CoreSettings.load(root));
        Files.writeString(
            root.resolve("config.toml"),
            "# emulator and Minecraft share this file\n"
                + "architecture = 'custom:lua'\n"
                + "storage_mode = 'direct'\n"
                + "worker_threads = 2\n"
                + "[machine]\n"
                + "budget_per_tick = 20_000\n");
        CoreSettings settings = CoreSettings.load(root);
        assertEquals("custom:lua", settings.architecture());
        assertEquals(CoreSettings.StorageMode.DIRECT, settings.storageMode());
        assertEquals(20000, settings.machine().budgetPerTick());
    }

    @Test
    void rejectsMalformedOutOfRangeAndWronglyTypedValues() throws Exception {
        for (String value :
            new String[]{
                "worker_threads = 0",
                "worker_threads = 'two'",
                "worker_threads = 99999999999",
                "[invalid"
            }) {
            Files.writeString(root.resolve("config.toml"), value);
            assertThrows(IOException.class, () -> CoreSettings.load(root));
        }
    }
}
