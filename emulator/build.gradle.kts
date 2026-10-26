plugins { application }

repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation(project(":common"))
    implementation(project(":architecture-shuttle"))
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.26.1")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
java.toolchain { languageVersion = JavaLanguageVersion.of(21) }
application {
    mainClass = "jp.akkiserver.rc.emulator.EmulatorMain"
    applicationDefaultJvmArgs = listOf("-Xss8m")
}
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xss8m")
    systemProperty("java.awt.headless", !providers.gradleProperty("guiSmoke").isPresent)
}
