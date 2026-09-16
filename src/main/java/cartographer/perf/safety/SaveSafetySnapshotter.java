package cartographer.perf.safety;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Captures only the main save and its three SQLite sidecars, without writes. */
public final class SaveSafetySnapshotter {
    private static final int HASH_BUFFER_SIZE = 16 * 1024;
    private static final String[] SIDECAR_SUFFIXES = {"-wal", "-shm", "-journal"};

    public SaveSafetySnapshot capture(Path savePath) {
        Path normalizedSavePath = normalize(savePath);
        SaveFileSnapshot mainSave = captureRequiredRegularFile(normalizedSavePath);
        SaveFileSnapshot wal = captureOptionalFile(
                normalizedSavePath.resolveSibling(normalizedSavePath.getFileName() + SIDECAR_SUFFIXES[0])
        );
        SaveFileSnapshot shm = captureOptionalFile(
                normalizedSavePath.resolveSibling(normalizedSavePath.getFileName() + SIDECAR_SUFFIXES[1])
        );
        SaveFileSnapshot journal = captureOptionalFile(
                normalizedSavePath.resolveSibling(normalizedSavePath.getFileName() + SIDECAR_SUFFIXES[2])
        );
        return new SaveSafetySnapshot(normalizedSavePath, mainSave, wal, shm, journal);
    }

    private SaveFileSnapshot captureRequiredRegularFile(Path path) {
        SaveFileSnapshot snapshot = capture(path, true);
        if (!snapshot.exists() || !snapshot.regularFile()) {
            throw new SaveSafetyException("Main save must be an existing regular file: " + path);
        }
        return snapshot;
    }

    private SaveFileSnapshot captureOptionalFile(Path path) {
        return capture(path, false);
    }

    private SaveFileSnapshot capture(Path path, boolean required) {
        BasicFileAttributes before = readAttributes(path, required);
        if (before == null) {
            return SaveFileSnapshot.absent(path);
        }
        if (!before.isRegularFile()) {
            return new SaveFileSnapshot(
                    path, true, false, OptionalLong.empty(), Optional.empty(), Optional.empty()
            );
        }

        SaveContentHash hash = hash(path);
        BasicFileAttributes after = readAttributes(path, true);
        if (after == null || !after.isRegularFile()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new SaveSafetyException("File changed while being captured: " + path);
        }
        return new SaveFileSnapshot(
                path,
                true,
                true,
                OptionalLong.of(after.size()),
                Optional.of(after.lastModifiedTime()),
                Optional.of(hash)
        );
    }

    private static BasicFileAttributes readAttributes(Path path, boolean required) {
        try {
            return Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
            );
        } catch (NoSuchFileException exception) {
            if (required) {
                throw new SaveSafetyException("Protected file disappeared: " + path, exception);
            }
            return null;
        } catch (IOException exception) {
            throw new SaveSafetyException("Cannot inspect protected file: " + path, exception);
        }
    }

    private static SaveContentHash hash(Path path) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
        byte[] buffer = new byte[HASH_BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new SaveSafetyException("Cannot hash protected file: " + path, exception);
        }
        return new SaveContentHash(HexFormat.of().formatHex(digest.digest()));
    }

    private static Path normalize(Path path) {
        Objects.requireNonNull(path, "savePath is required");
        return path.toAbsolutePath().normalize();
    }
}
