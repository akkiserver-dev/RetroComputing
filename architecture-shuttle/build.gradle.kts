plugins { `java-library` }

repositories { mavenLocal(); mavenCentral() }

dependencies {
    api(project(":common"))
    implementation("jp.akkiserver:shuttle-java:6.0.1-SNAPSHOT")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java.toolchain { languageVersion = JavaLanguageVersion.of(21) }
tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xss8m")
    workingDir = rootProject.projectDir
    providers.gradleProperty("loadBenchmarkImage").orNull?.let { image ->
        systemProperty("rc.shuttle.loadBenchmarkImage", image)
        systemProperty("rc.shuttle.loadTimings", "true")
        testLogging.showStandardStreams = true
    }
}
