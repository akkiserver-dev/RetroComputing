plugins {
    `java-library`
    id("common-config-conventions")
}

repositories {
    exclusiveContent {
        forRepository { maven { name = "RC Config Dependencies"; url = uri("https://repo.maven.apache.org/maven2") } }
        filter {
            includeGroup("org.tomlj")
            includeGroup("org.antlr")
            includeGroup("org.checkerframework")
        }
    }
    mavenCentral()
}

dependencies {
    add("configCompileOnly", "org.jspecify:jspecify:1.0.0")
    implementation("org.tomlj:tomlj:1.1.1")
    // Match the oldest supported loader classpath: 1.18.2 resolves slf4j-api to 1.8.0-beta4.
    compileOnly(libs.slf4j.api)

    // JUnit
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(21)
    @Suppress("UnstableApiUsage")
    vendor = JvmVendorSpec.JETBRAINS
}
