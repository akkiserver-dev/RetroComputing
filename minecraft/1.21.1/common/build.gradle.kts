plugins {
    id("neoforge-common-conventions")
    id("neoforge-common-config-conventions")
}

dependencies {
    add("configCompileOnly", "org.jspecify:jspecify:1.0.0")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}
tasks.test { useJUnitPlatform() }
