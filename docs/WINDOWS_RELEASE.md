# Windows Release Structure

## Entry points

VS Cartographer has two entry points:

- CLI: `cartographer.Main`
- Desktop: `cartographer.ui.CartographerDesktopLauncher`

The Gradle `application.mainClass` intentionally remains `cartographer.Main` so
the existing CLI application workflow is unchanged. Windows packaging
explicitly selects `cartographer.ui.CartographerDesktopLauncher`.

## Packaging input

Stage 1 provides the Gradle preparation task:

```text
./gradlew prepareJpackageInput
```

It stages the application JAR and all JARs from the runtime classpath in:

```text
build/jpackage/input/
```

This is the non-modular classpath input for the future Windows packaging step.

## Stage 2: portable Windows app image

Stage 2 adds Windows-specific Gradle tasks using the JDK 25 `jpackage` tool.
The app-image uses the desktop launcher and contains its own generated Java
runtime. The application remains a non-modular classpath application; no custom
runtime image is supplied.

Run on Windows with:

```powershell
.\gradlew.bat packageWindowsAppImage
```

The expected output is:

```text
build/jpackage/app-image/VS Cartographer/
├── VS Cartographer.exe
├── app/
└── runtime/
```

The packaging task resolves `jpackage.exe` from the project's Java 25 Gradle
toolchain and fails when invoked on a non-Windows host. The packaged launcher
receives `--enable-native-access=ALL-UNNAMED`.

The complete app-image can be distributed as a portable ZIP:

```powershell
.\gradlew.bat packageWindowsPortable
```

The expected output is:

```text
build/distributions/VS-Cartographer-1.0-SNAPSHOT-win-x64.zip
```

The ZIP preserves the top-level `VS Cartographer/` directory and contains the
complete app-image, including `app/` and `runtime/`.

The intended release flow is:

```text
prepareJpackageInput
        ↓
jpackage --type app-image (Stage 2)
        ↓
portable Windows application
        ↓
jpackage Windows installer (FUTURE WORK)
```

Installer creation remains future work. Icon customization is also future work;
Stage 2 does not provide an icon.

## Runtime

The project targets Java 25. Packaged launchers retain the equivalent of:

```text
--enable-native-access=ALL-UNNAMED
```

The desktop packaging metadata is intended for the application name
`VS Cartographer`, desktop main class
`cartographer.ui.CartographerDesktopLauncher`, and vendor `MariuszKam`.

## Non-goals

Stage 2 does not:

- create an MSI;
- create an installer;
- change CLI behavior;
- change save handling;
- change application storage;
- change runtime semantics.

Stage 2 does not create an installer, add file associations, or supply a custom
runtime image. The executable and native runtime have not been tested or
verified by this documentation.
