package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerVerifierTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsExactSizeAndSha256() throws Exception {
        byte[] bytes = "verified-installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, bytes);

        assertTrue(new UpdateInstallerVerifier().isVerified(
                installer,
                manifest(bytes)
        ));
    }

    @Test
    void rejectsChangedInstaller() throws Exception {
        byte[] expected = "verified-installer".getBytes();
        Path installer = temporaryDirectory.resolve("installer.exe");
        Files.write(installer, "tampered-installer".getBytes());

        assertFalse(new UpdateInstallerVerifier().isVerified(
                installer,
                manifest(expected)
        ));
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
