import net.meatwo310.mdk.build.VersionCatalogLibrary
import net.meatwo310.mdk.build.VersionConfigSourceSet
import net.meatwo310.mdk.build.configureFabricConfigConvention
import net.meatwo310.mdk.build.disallowConfigConvention

val minecraftVersion = project.property("minecraftVersion").toString()
val forgeConfigApiPortVersion = project.property("forgeConfigApiPortVersion").toString()

disallowConfigConvention("fabric-legacy-config-conventions", "fabric-config-conventions")
configureFabricConfigConvention(
    minecraftVersion,
    forgeConfigApiPortVersion,
    "fabric-config-conventions",
    VersionCatalogLibrary.ForgeConfigApiPortFabric,
    VersionConfigSourceSet.REQUIRED,
)
