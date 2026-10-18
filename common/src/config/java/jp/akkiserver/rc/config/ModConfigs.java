package jp.akkiserver.rc.config;

import jp.akkiserver.rc.mdk.config.ConfigDeclaration;
import jp.akkiserver.rc.mdk.config.ConfigSide;

import java.util.List;

public final class ModConfigs {
    public static final ConfigDeclaration SERVER = ConfigDeclaration.of(ConfigSide.SYNCED, ServerConfig.ENTRIES);

    public static final List<ConfigDeclaration> ALL = List.of(SERVER);

    private ModConfigs() {}
}
