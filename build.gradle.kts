import java.util.Locale
import java.io.File
import java.security.MessageDigest
import org.gradle.api.GradleException
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    application
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("cartographer.test-architecture")
    id("cartographer.test-performance")
}

group = "cartographer"

val stableVersionPattern = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
val releaseVersion = project.version.toString()
if (!stableVersionPattern.matches(releaseVersion)) {
    throw GradleException(
        "Project version must be stable SemVer MAJOR.MINOR.PATCH, got '$releaseVersion'"
    )
}

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
val generatedBuildInfoDirectory = layout.buildDirectory.dir("generated/resources/build-info")
val generatedBuildInfoFile = generatedBuildInfoDirectory.map {
    it.file("cartographer-build.properties")
}
val updateManifestFile = layout.buildDirectory.file("release-validation/update.properties")
val jpackageJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}
val jmhJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
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

val generateBuildInfo = tasks.register("generateBuildInfo") {
    group = "build"
    description = "Generates runtime build metadata from the Gradle project version"
    inputs.property("version", releaseVersion)
    outputs.file(generatedBuildInfoFile)

    doLast {
        val output = generatedBuildInfoFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("version=$releaseVersion\n", Charsets.UTF_8)
    }
}

sourceSets.named("main") {
    resources.srcDir(generatedBuildInfoDirectory)
}

tasks.named("processResources") {
    dependsOn(generateBuildInfo)
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the canonical VS Cartographer project version"
    doLast {
        println(releaseVersion)
    }
}

tasks.register("verifyReleaseTag") {
    group = "verification"
    description = "Verifies that a release tag exactly matches the canonical project version"

    doLast {
        val tag = providers.gradleProperty("releaseTag").orNull
            ?: providers.environmentVariable("GITHUB_REF_NAME").orNull
            ?: throw GradleException(
                "Missing release tag. Pass -PreleaseTag=v$releaseVersion or set GITHUB_REF_NAME."
            )
        val expected = "v$releaseVersion"
        if (tag != expected) {
            throw GradleException(
                "Release tag '$tag' does not match project version '$releaseVersion'. Expected '$expected'."
            )
        }
    }
}

repositories {
    mavenCentral()
}

val jmhSourceSet = sourceSets.create("jmh") {
    java.srcDir("src/jmh/java")
    compileClasspath += sourceSets["main"].output
    compileClasspath += configurations["runtimeClasspath"]
    runtimeClasspath += sourceSets["main"].output
    runtimeClasspath += configurations["runtimeClasspath"]
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.github.luben:zstd-jni:1.5.6-9")
    implementation("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.jetbrains:annotations:26.1.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    add("jmhImplementation", "org.openjdk.jmh:jmh-core:1.37")
    add("jmhAnnotationProcessor", "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
    mainClass = packagingDesktopMainClass
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
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

tasks.named<JavaCompile>("compileJmhJava") {
    options.annotationProcessorPath = configurations["jmhAnnotationProcessor"]
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs opt-in JMH microbenchmarks; output is not macro benchmark evidence"
    dependsOn("jmhClasses")
    classpath = jmhSourceSet.runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    javaLauncher.set(jmhJavaLauncher)
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

tasks.register("generateUpdateManifest") {
    group = "distribution"
    description = "Generates the stable-channel update manifest for a GitHub release"
    dependsOn("verifyReleaseTag")
    inputs.property("version", releaseVersion)
    inputs.file(canonicalInstallerFile)
    inputs.property(
        "releaseTag",
        providers.gradleProperty("releaseTag")
            .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
            .orElse("")
    )
    inputs.property(
        "releaseRepository",
        providers.gradleProperty("releaseRepository")
            .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
            .orElse("")
    )
    outputs.file(updateManifestFile)

    doLast {
        val repository = providers.gradleProperty("releaseRepository").orNull
            ?: providers.environmentVariable("GITHUB_REPOSITORY").orNull
            ?: throw GradleException(
                "Missing release repository. Pass -PreleaseRepository=owner/repository or set GITHUB_REPOSITORY."
            )
        if (!repository.matches(Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"""))) {
            throw GradleException(
                "Release repository must use owner/repository form, got '$repository'"
            )
        }

        val tag = providers.gradleProperty("releaseTag").orNull
            ?: providers.environmentVariable("GITHUB_REF_NAME").orNull
            ?: throw GradleException(
                "Missing release tag. Pass -PreleaseTag=v$releaseVersion or set GITHUB_REF_NAME."
            )
        val expectedTag = "v$releaseVersion"
        if (tag != expectedTag) {
            throw GradleException(
                "Release tag '$tag' does not match project version '$releaseVersion'. Expected '$expectedTag'."
            )
        }

        val installer = canonicalInstallerFile.get().asFile
        if (!installer.isFile || installer.length() <= 0L) {
            throw GradleException(
                "Canonical Windows installer is missing or empty: ${installer.absolutePath}"
            )
        }

        val releaseBase = "https://github.com/$repository/releases"
        val manifest = buildString {
            appendLine("schemaVersion=1")
            appendLine("channel=stable")
            appendLine("version=$releaseVersion")
            appendLine("installerFile=${installer.name}")
            appendLine("installerUrl=$releaseBase/download/$tag/${installer.name}")
            appendLine("installerSha256=${sha256(installer)}")
            appendLine("installerSize=${installer.length()}")
            appendLine("releaseUrl=$releaseBase/tag/$tag")
        }

        val output = updateManifestFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(manifest, Charsets.UTF_8)
    }
}
