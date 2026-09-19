package cartographer.application;

import java.nio.file.Path;
import java.util.Objects;

public record PrepareWorldSnapshotRequest(Path savePath) {
    public PrepareWorldSnapshotRequest {
        savePath = Objects.requireNonNull(savePath, "savePath is required")
                .toAbsolutePath()
                .normalize();
    }
}
