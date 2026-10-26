package jp.akkiserver.rc;

import jp.akkiserver.rc.config.ModConfigs;
import jp.akkiserver.rc.mdk.config.PlatformConfigRegistrar;
import jp.akkiserver.rc.mdk.config.VersionedConfigSpec;
import net.fabricmc.api.ModInitializer;

public class ModMain implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOGGER.debug(Constants.INITIALIZING, ModUtils.loc("1.21.1-fabric"));
        PlatformConfigRegistrar.registerAll(Constants.MODID, VersionedConfigSpec.bindAll(ModConfigs.ALL));
    }
}
