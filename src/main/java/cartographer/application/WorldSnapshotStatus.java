package cartographer.application;

import cartographer.perf.WorldSnapshotPreparationSummary;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record WorldSnapshotStatus(
        Path savePath,
        String revisionHash,
        State state,
        Optional<WorldSnapshotPreparationSummary> summary
) {
    public enum State {
        NOT_PREPARED,
        PARTIAL,
        READY
    }

    public WorldSnapshotStatus {
        savePath = Objects.requireNonNull(savePath, "savePath is required")
                .toAbsolutePath()
                .normalize();
        revisionHash = Objects.requireNonNull(
                revisionHash,
                "revisionHash is required"
        ).trim();
        if (revisionHash.isEmpty()) {
            throw new IllegalArgumentException(
                    "revisionHash must not be blank"
            );
        }
        state = Objects.requireNonNull(state, "state is required");
        summary = Objects.requireNonNull(summary, "summary is required");
        if (state == State.READY
                && summary.filter(
                WorldSnapshotPreparationSummary::complete
        ).isEmpty()) {
            throw new IllegalArgumentException(
                    "READY snapshot requires a complete summary"
            );
        }
    }

    public String shortRevision() {
        return revisionHash.length() <= 8
                ? revisionHash
                : revisionHash.substring(0, 8);
    }
}
