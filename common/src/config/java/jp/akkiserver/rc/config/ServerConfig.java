package jp.akkiserver.rc.config;

import jp.akkiserver.rc.mdk.config.ConfigEntries;
import jp.akkiserver.rc.mdk.config.ConfigEntryBuilder;

public class ServerConfig {
    private static final ConfigEntryBuilder BUILDER = new ConfigEntryBuilder();

    public static final ConfigEntries ENTRIES = BUILDER.build();
}
