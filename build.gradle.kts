import java.util.Locale
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    application
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "cartographer"
version = "1.0.0"

val packagingApplicationName = "VS Cartographer"
val packagingDesktopMainClass = "cartographer.ui.CartographerDesktopLauncher"
val packagingVendor = "MariuszKam"
val packagingVersion = project.version.toString()
val packagingDescription = "Offline Vintage Story save cartographer and world analysis tool"
val packagingCopyright = "Copyright © 2026 MariuszKam"
val packagingWindowsUpgradeUuid = "d9458212-ecd8-4882-8d90-ffba8ade0c4f"
val packagingIcon = layout.projectDirectory.file("src/main/packaging/vs-cartographer.ico")
val jpackageInputDirectory = layout.buildDirectory.dir("jpackage/input")
val jpackageAppImageDirectory = layout.buildDirectory.dir("jpackage/app-image")
val jpackageAppImage = jpackageAppImageDirectory.map { it.dir(packagingApplicationName) }
val jpackageInstallerDirectory = layout.buildDirectory.dir("jpackage/installer")
val canonicalInstallerFileName = "VS-Cartographer-Setup-${project.version}.exe"
val canonicalInstallerFile = layout.buildDirectory.file("distributions/$canonicalInstallerFileName")
val jpackageJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}

fun resolveJpackageExecutable(): File {
    val operatingSystem = System.getProperty("os.name").lowercase(Locale.ROOT)
    if (!operatingSystem.contains("win")) {
        throw GradleException("Windows packaging is supported only on Windows hosts")
    }

    val jpackage = jpackageJavaLauncher.get().metadata.installationPath
        .file("bin/jpackage.exe")
        .asFile
    if (!jpackage.isFile) {
        throw GradleException("Java 25 toolchain does not contain jpackage.exe: $jpackage")
    }
    return jpackage
}

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
    mainClass.set(packagingDesktopMainClass)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<Sync>("prepareJpackageInput") {
    group = "distribution"
    description = "Stages the application JAR and runtime dependencies for future jpackage use"
    dependsOn(tasks.jar)
    into(jpackageInputDirectory)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
}

val cleanWindowsAppImage = tasks.register<Delete>("cleanWindowsAppImage") {
    description = "Removes only the generated Windows app-image output"
    delete(jpackageAppImageDirectory)
}

val packageWindowsAppImage = tasks.register<Exec>("packageWindowsAppImage") {
    group = "distribution"
    description = "Creates the portable Windows app-image with the Java 25 jpackage tool"
    dependsOn(tasks.named("prepareJpackageInput"), cleanWindowsAppImage)

    doFirst {
        val jpackage = resolveJpackageExecutable()
        val applicationJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile.name
        val jpackageArguments = mutableListOf(
            "--type", "app-image",
            "--name", packagingApplicationName,
            "--app-version", packagingVersion,
            "--description", packagingDescription,
            "--copyright", packagingCopyright,
            "--vendor", packagingVendor,
            "--input", jpackageInputDirectory.get().asFile.absolutePath,
            "--main-jar", applicationJar,
            "--main-class", packagingDesktopMainClass,
            "--dest", jpackageAppImageDirectory.get().asFile.absolutePath,
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )
        if (packagingIcon.asFile.isFile) {
            jpackageArguments.add("--icon")
            jpackageArguments.add(packagingIcon.asFile.absolutePath)
        }
        commandLine(listOf(jpackage.absolutePath) + jpackageArguments)
    }
}

tasks.register<Zip>("packageWindowsPortable") {
    group = "distribution"
    description = "Packages the complete Windows app-image as a portable ZIP"
    dependsOn(packageWindowsAppImage)
    archiveFileName.set("VS-Cartographer-${project.version}-win-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(jpackageAppImageDirectory)
}

val cleanWindowsInstallerOutput = tasks.register<Delete>("cleanWindowsInstallerOutput") {
    description = "Removes only generated Windows installer output"
    delete(jpackageInstallerDirectory, canonicalInstallerFile)
}

val createWindowsInstaller = tasks.register<Exec>("createWindowsInstaller") {
    description = "Creates the raw Windows EXE installer from the existing app-image"
    dependsOn(packageWindowsAppImage, cleanWindowsInstallerOutput)

    doFirst {
        val jpackage = resolveJpackageExecutable()
        val jpackageArguments = mutableListOf(
            "--type", "exe",
            "--app-image", jpackageAppImage.get().asFile.absolutePath,
            "--name", packagingApplicationName,
            "--app-version", packagingVersion,
            "--vendor", packagingVendor,
            "--description", packagingDescription,
            "--copyright", packagingCopyright,
            "--dest", jpackageInstallerDirectory.get().asFile.absolutePath,
            "--win-per-user-install",
            "--win-menu",
            "--win-menu-group", packagingApplicationName,
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-upgrade-uuid", packagingWindowsUpgradeUuid
        )
        if (packagingIcon.asFile.isFile) {
            jpackageArguments.add("--icon")
            jpackageArguments.add(packagingIcon.asFile.absolutePath)
        }
        commandLine(listOf(jpackage.absolutePath) + jpackageArguments)
    }
}

tasks.register("packageWindowsInstaller") {
    group = "distribution"
    description = "Creates the Windows EXE installer from the existing app-image"
    dependsOn(createWindowsInstaller)

    doLast {
        val candidates = jpackageInstallerDirectory.get().asFile
            .listFiles { file -> file.isFile && file.extension.equals("exe", ignoreCase = true) }
            ?.toList()
            ?: emptyList()
        if (candidates.size != 1) {
            throw GradleException(
                "Expected exactly one EXE installer in ${jpackageInstallerDirectory.get().asFile}, "
                    + "found ${candidates.size}"
            )
        }

        val destination = canonicalInstallerFile.get().asFile
        destination.parentFile.mkdirs()
        candidates.single().copyTo(destination, overwrite = true)
    }
}
