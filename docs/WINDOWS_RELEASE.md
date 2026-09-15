# Windows Release Structure

## Entry points

VS Cartographer has two entry points:

- CLI: `cartographer.Main`
- Desktop: `cartographer.ui.CartographerDesktopLauncher`

The Gradle `application.mainClass` intentionally remains `cartographer.Main` so
the existing CLI application workflow is unchanged. Future Windows packaging
will explicitly select `cartographer.ui.CartographerDesktopLauncher`.

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

## Planned Windows distribution

The intended release flow is:

```text
prepareJpackageInput
        ↓
jpackage --type app-image
        ↓
portable Windows application
        ↓
jpackage Windows installer
```

The native `jpackage` app-image and installer steps are FUTURE WORK and are not
implemented in Stage 1.

## Runtime

The project targets Java 25. Future packaged launchers must retain the
equivalent of:

```text
--enable-native-access=ALL-UNNAMED
```

The desktop packaging metadata is intended for the application name
`VS Cartographer`, desktop main class
`cartographer.ui.CartographerDesktopLauncher`, and vendor `MariuszKam`.

## Non-goals

Stage 1 does not:

- create an EXE;
- create an MSI;
- create an installer;
- change CLI behavior;
- change save handling;
- change application storage;
- change runtime semantics.

No tests or runtime validation are claimed by this documentation.
