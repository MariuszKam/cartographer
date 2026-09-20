package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsUpdateBootstrapperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesExternalBootstrapAndStartsHiddenPowerShell()
            throws Exception {
        byte[] bytes = "installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, bytes);
        Path launcher = temporaryDirectory.resolve("VS Cartographer.exe");
        Files.writeString(launcher, "launcher");
        Path updatesRoot = temporaryDirectory.resolve("updates");
        AtomicReference<List<String>> command = new AtomicReference<>();

        WindowsUpdateBootstrapper bootstrapper =
                new WindowsUpdateBootstrapper(
                        updatesRoot,
                        () -> 4242L,
                        () -> Optional.of(launcher),
                        command::set,
                        () -> "Windows 11"
                );

        Files.createDirectories(
                bootstrapper.outcomePath().getParent()
        );
        Files.writeString(
                bootstrapper.outcomePath(),
                "stale=true\n"
        );

        bootstrapper.launch(manifest(bytes), installer);

        assertFalse(Files.exists(bootstrapper.outcomePath()));
        List<String> launched = command.get();
        assertTrue(launched.contains("powershell.exe"));
        assertTrue(launched.contains("-WindowStyle"));
        assertTrue(launched.contains("Hidden"));
        assertTrue(launched.contains("4242"));
        assertTrue(launched.contains(installer.toAbsolutePath().toString()));
        assertTrue(launched.contains(launcher.toAbsolutePath().toString()));

        int fileArgument = launched.indexOf("-File");
        Path script = Path.of(launched.get(fileArgument + 1));
        String scriptText = Files.readString(script);
        assertTrue(scriptText.contains("Wait-Process -Id $TargetPid"));
        assertTrue(scriptText.contains("Get-FileHash"));
        assertTrue(scriptText.contains("-PassThru -Wait"));
        assertTrue(scriptText.contains("Restart-Cartographer"));
    }

    @Test
    void refusesRestartUpdateOutsideWindows() throws Exception {
        Path installer = temporaryDirectory.resolve("installer.exe");
        byte[] bytes = "installer".getBytes();
        Files.write(installer, bytes);
        Path launcher = temporaryDirectory.resolve("VS Cartographer.exe");
        Files.writeString(launcher, "launcher");

        WindowsUpdateBootstrapper bootstrapper =
                new WindowsUpdateBootstrapper(
                        temporaryDirectory.resolve("updates"),
                        () -> 42L,
                        () -> Optional.of(launcher),
                        ignored -> { },
                        () -> "Linux"
                );

        assertThrows(
                java.io.IOException.class,
                () -> bootstrapper.launch(manifest(bytes), installer)
        );
    }

    @Test
    void refusesDevelopmentJavaProcessAsRelaunchTarget()
            throws Exception {
        Path installer = temporaryDirectory.resolve("installer.exe");
        byte[] bytes = "installer".getBytes();
        Files.write(installer, bytes);
        Path java = temporaryDirectory.resolve("java.exe");
        Files.writeString(java, "java");

        WindowsUpdateBootstrapper bootstrapper =
                new WindowsUpdateBootstrapper(
                        temporaryDirectory.resolve("updates"),
                        () -> 42L,
                        () -> Optional.of(java),
                        ignored -> { },
                        () -> "Windows 11"
                );

        assertThrows(
                java.io.IOException.class,
                () -> bootstrapper.launch(manifest(bytes), installer)
        );
    }

    private UpdateManifest manifest(byte[] bytes) throws Exception {
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
        return new UpdateManifest(
                1,
                "stable",
                ApplicationVersion.parse("1.1.0"),
                "VS-Cartographer-Setup-1.1.0.exe",
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "download/v1.1.0/"
                                + "VS-Cartographer-Setup-1.1.0.exe"
                ),
                hash,
                bytes.length,
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "tag/v1.1.0"
                )
        );
    }
}
