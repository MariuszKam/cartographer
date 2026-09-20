package cartographer.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class UpdateInstallerVerifier {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    public boolean isVerified(
            Path file,
            UpdateManifest manifest
    ) throws IOException {
        Objects.requireNonNull(file, "file is required");
        Objects.requireNonNull(manifest, "manifest is required");

        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (Files.size(file) != manifest.installerSize()) {
            return false;
        }
        return manifest.installerSha256().equals(sha256(file));
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
