pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            name = "GTNH Maven"
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


/// Common ///
include("common")
include("architecture-shuttle")
include("emulator")

fun includeMc(minecraftVersion: String, kind: String) {
    val projectName = "$minecraftVersion-$kind"
    include(projectName)
    project(":$projectName").projectDir = file("minecraft/$minecraftVersion/$kind")
}

/// 1.21.1 ///
includeMc("1.21.1", "common")
includeMc("1.21.1", "neo")
includeMc("1.21.1", "fabric")

/*
/// 26.1.2 ///
includeMc("26.1.2", "common")
includeMc("26.1.2", "fabric")
includeMc("26.1.2", "neo")

/// 26.2 ///
includeMc("26.2", "common")
includeMc("26.2", "fabric")
includeMc("26.2", "neo")

/// 26.3 ///
includeMc("26.3", "common")
includeMc("26.3", "fabric")
includeMc("26.3", "neo")
*/

val ciBuildProjectNames = rootProject.children
    .map { it.name }
    .filterNot { it == "common" || it == "architecture-shuttle" || it == "emulator" || it.endsWith("-common") }

gradle.extensions.extraProperties["ciBuildProjectNames"] = ciBuildProjectNames
