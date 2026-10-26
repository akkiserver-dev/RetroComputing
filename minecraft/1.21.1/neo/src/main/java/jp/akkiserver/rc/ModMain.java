package jp.akkiserver.rc;

import jp.akkiserver.rc.config.ModConfigs;
import jp.akkiserver.rc.mdk.config.PlatformConfigRegistrar;
import jp.akkiserver.rc.mdk.config.VersionedConfigSpec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MODID)
public class ModMain {
    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        Constants.LOGGER.debug(Constants.INITIALIZING, ModUtils.loc("1.21.1-neo"));
        PlatformConfigRegistrar.registerAll(modContainer, VersionedConfigSpec.bindAll(ModConfigs.ALL));
    }
}
