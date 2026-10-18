package jp.akkiserver.rc.api.hardware.device;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;

/**
 * Per-device storage for large data (disk contents and the like).
 * Devices never create files themselves; they go through this interface so
 * that the machine decides when writes become durable.
 *
 * @since 0.1.0
 * @author akki697222
 */
public interface DeviceStorage {
    SeekableByteChannel open(String name, OpenOption... options) throws IOException;

    boolean exists(String name);

    void delete(String name) throws IOException;
}
