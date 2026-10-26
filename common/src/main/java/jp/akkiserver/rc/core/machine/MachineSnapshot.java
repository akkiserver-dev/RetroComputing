package jp.akkiserver.rc.core.machine;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.data.NbtIO;

import java.io.IOException;

/**
 * Self-contained snapshot. Disk contents are committed separately in the same storage generation.
 */
public final class MachineSnapshot {
    private final MapDataCompound data;

    public MachineSnapshot(DataCompound data) {
        this.data = MapDataCompound.copyOf(data);
        if (this.data.getInt("version") != 1
            || this.data.getString("architecture") == null
            || this.data.getByteArray("runtime") == null
            || this.data.getByteArray("boot") == null)
            throw new IllegalArgumentException("Invalid machine snapshot");
    }

    public static MachineSnapshot read(byte[] bytes) throws IOException {
        return new MachineSnapshot(NbtIO.read(bytes));
    }

    public MapDataCompound data() {
        return data.copy();
    }

    public byte[] write() throws IOException {
        return data.write();
    }
}
