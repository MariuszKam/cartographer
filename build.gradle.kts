plugins {
    application
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "cartographer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.github.luben:zstd-jni:1.5.6-9")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "cartographer.Main"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

javafx {
    version = "25"
    modules("javafx.controls", "javafx.swing")
}

tasks.register<JavaExec>("runGui") {
    group = "application"
    description = "Launches the VS Cartographer desktop UI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cartographer.ui.CartographerDesktopLauncher")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
