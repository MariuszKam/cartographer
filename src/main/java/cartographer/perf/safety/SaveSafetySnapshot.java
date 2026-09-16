package cartographer.perf.safety;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable protected filesystem snapshot for one normalized main save path. */
public record SaveSafetySnapshot(
        Path savePath,
        SaveFileSnapshot mainSave,
        SaveFileSnapshot wal,
        SaveFileSnapshot shm,
        SaveFileSnapshot journal
) {
    public SaveSafetySnapshot {
        Objects.requireNonNull(savePath, "savePath is required");
        savePath = savePath.toAbsolutePath().normalize();
        Objects.requireNonNull(mainSave, "mainSave is required");
        Objects.requireNonNull(wal, "wal is required");
        Objects.requireNonNull(shm, "shm is required");
        Objects.requireNonNull(journal, "journal is required");
        if (!mainSave.exists() || !mainSave.regularFile()) {
            throw new IllegalArgumentException("mainSave must be an existing regular file");
        }
        if (!savePath.equals(mainSave.path())) {
            throw new IllegalArgumentException("mainSave path must match savePath");
        }
    }
}
