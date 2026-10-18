package jp.akkiserver.rc.api.hardware.device;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.Hardware;

/**
 * @since 0.1.0
 * @author akki697222
 */
public interface Device extends Hardware {
    @Override
    DeviceInfo info();

    //default void methods(MethodTable t) { t.registerAnnotated(this); }

    //default void attached(DeviceContext ctx) {}
    default void detached() {}

    default void save(DataCompound out) {}
    default void load(DataCompound in) {}

    default int dataVersion() { return 1; }
    default void migrate(DataCompound data, int fromVersion) {
        throw new IllegalStateException("no migration from v" + fromVersion);
    }
}
