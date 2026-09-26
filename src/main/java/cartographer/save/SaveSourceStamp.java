package cartographer.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Filesystem evidence that the authoritative save and its SQLite sidecars stayed
 * quiescent for the lifetime of one save session.
 */
record SaveSourceStamp(
        FileStamp database,
        FileStamp wal,
        FileStamp shm
) {
    SaveSourceStamp {
        Objects.requireNonNull(database, "database stamp is required");
        Objects.requireNonNull(wal, "wal stamp is required");
        Objects.requireNonNull(shm, "shm stamp is required");
    }

    static SaveSourceStamp capture(Path savePath) {
        Path normalized = SavePathIdentity.normalize(savePath);
        return new SaveSourceStamp(
                FileStamp.capture(normalized),
                FileStamp.capture(sidecar(normalized, "-wal")),
                FileStamp.capture(sidecar(normalized, "-shm"))
        );
    }

    void requireUnchanged(Path savePath) {
        SaveSourceStamp current = capture(savePath);
        if (!equals(current)) {
            throw new SaveException(
                    "Save changed during analysis: "
                            + SavePathIdentity.normalize(savePath)
                            + ". Close Vintage Story before analyzing a save and retry."
            );
        }
    }

    private static Path sidecar(Path savePath, String suffix) {
        return Path.of(savePath.toString() + suffix);
    }

    private record FileStamp(
            boolean exists,
            long size,
            FileTime lastModifiedTime,
            String fileKey
    ) {
        private static FileStamp capture(Path path) {
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    return new FileStamp(false, 0L, FileTime.fromMillis(0L), "");
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        path,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
                return new FileStamp(
                        true,
                        attributes.size(),
                        attributes.lastModifiedTime(),
                        Objects.toString(attributes.fileKey(), "")
                );
            } catch (IOException exception) {
                throw new SaveException(
                        "Cannot inspect save source state: " + path,
                        exception
                );
            }
        }
    }
}
