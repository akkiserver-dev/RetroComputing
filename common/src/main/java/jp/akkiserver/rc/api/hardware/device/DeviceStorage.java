package jp.akkiserver.rc.api.hardware.device;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;

/**
 * Per-device storage for large data (disk contents and the like).
 * Devices never create files themselves; they go through this interface so
 * that the machine decides when writes become durable.
 *
 * @author akki697222
 * @since 0.1.0
 */
public interface DeviceStorage {
    /**
     * Opens a named storage channel using the requested options.
     *
     * @param name storage entry name
     * @param options options controlling how the entry is opened
     * @return a seekable channel for the entry
     * @throws IOException if the entry cannot be opened
     */
    SeekableByteChannel open(String name, OpenOption... options) throws IOException;

    /**
     * Reports whether a named storage entry exists.
     *
     * @param name storage entry name
     * @return whether the entry exists
     */
    boolean exists(String name);

    /**
     * Deletes a named storage entry.
     *
     * @param name storage entry name
     * @throws IOException if the entry cannot be deleted
     */
    void delete(String name) throws IOException;
}
