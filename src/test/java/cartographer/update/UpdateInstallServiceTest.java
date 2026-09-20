package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateInstallServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void revalidatesInstallerBeforeStartingBootstrap() throws Exception {
        byte[] bytes = "installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, bytes);
        UpdateManifest manifest = manifest(bytes);
        AtomicInteger launches = new AtomicInteger();

        UpdateInstallLaunchResult result = new UpdateInstallService(
                new UpdateInstallerVerifier(),
                (ignoredManifest, ignoredPath) -> launches.incrementAndGet()
        ).launch(UpdateDownloadResult.ready(manifest, installer));

        assertEquals(
                UpdateInstallLaunchResult.Status.STARTED,
                result.status()
        );
        assertEquals(1, launches.get());
    }

    @Test
    void changedInstallerFailsClosedBeforeBootstrap() throws Exception {
        byte[] expected = "installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, "changed".getBytes());
        AtomicInteger launches = new AtomicInteger();

        UpdateInstallLaunchResult result = new UpdateInstallService(
                new UpdateInstallerVerifier(),
                (ignoredManifest, ignoredPath) -> launches.incrementAndGet()
        ).launch(UpdateDownloadResult.ready(
                manifest(expected),
                installer
        ));

        assertEquals(
                UpdateInstallLaunchResult.Status.INVALID_INSTALLER,
                result.status()
        );
        assertEquals(0, launches.get());
    }

    @Test
    void bootstrapFailureLeavesInstallerRetryable() throws Exception {
        byte[] bytes = "installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, bytes);

        UpdateInstallLaunchResult result = new UpdateInstallService(
                new UpdateInstallerVerifier(),
                (ignoredManifest, ignoredPath) -> {
                    throw new IOException("PowerShell unavailable");
                }
        ).launch(UpdateDownloadResult.ready(
                manifest(bytes),
                installer
        ));

        assertEquals(
                UpdateInstallLaunchResult.Status.FAILED,
                result.status()
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
