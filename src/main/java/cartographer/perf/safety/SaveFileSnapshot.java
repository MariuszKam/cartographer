package cartographer.perf.safety;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable filesystem state for one protected save file or sidecar. */
public record SaveFileSnapshot(
        Path path,
        boolean exists,
        boolean regularFile,
        OptionalLong sizeBytes,
        Optional<FileTime> lastModified,
        Optional<SaveContentHash> sha256
) {
    public SaveFileSnapshot {
        Objects.requireNonNull(path, "path is required");
        path = path.toAbsolutePath().normalize();
        Objects.requireNonNull(sizeBytes, "sizeBytes is required");
        Objects.requireNonNull(lastModified, "lastModified is required");
        Objects.requireNonNull(sha256, "sha256 is required");
        if (!exists && regularFile) {
            throw new IllegalArgumentException("a missing file cannot be regular");
        }
        if (!exists || !regularFile) {
            if (sizeBytes.isPresent() || lastModified.isPresent() || sha256.isPresent()) {
                throw new IllegalArgumentException(
                        "metadata is only valid for an existing regular file"
                );
            }
        } else if (sizeBytes.isEmpty() || sizeBytes.getAsLong() < 0
                || lastModified.isEmpty() || sha256.isEmpty()) {
            throw new IllegalArgumentException(
                    "an existing regular file requires complete metadata"
            );
        }
    }

    public static SaveFileSnapshot absent(Path path) {
        return new SaveFileSnapshot(
                path, false, false, OptionalLong.empty(), Optional.empty(), Optional.empty()
        );
    }
}
