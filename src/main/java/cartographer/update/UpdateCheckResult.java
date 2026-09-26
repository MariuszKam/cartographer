package cartographer.update;

import java.util.Objects;
import java.util.Optional;

public record UpdateCheckResult(
        Status status,
        ApplicationVersion currentVersion,
        Optional<UpdateManifest> manifest,
        Optional<String> failureMessage
) {
    public enum Status {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        CHECK_FAILED
    }

    public UpdateCheckResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(
                currentVersion,
                "currentVersion is required"
        );
        Objects.requireNonNull(manifest, "manifest is required");
        Objects.requireNonNull(
                failureMessage,
                "failureMessage is required"
        );

        if (status == Status.UPDATE_AVAILABLE && manifest.isEmpty()) {
            throw new IllegalArgumentException(
                    "UPDATE_AVAILABLE requires a manifest"
            );
        }
        if (status != Status.UPDATE_AVAILABLE && manifest.isPresent()) {
            throw new IllegalArgumentException(
                    status + " cannot carry an update manifest"
            );
        }
        if (status == Status.CHECK_FAILED && failureMessage.isEmpty()) {
            throw new IllegalArgumentException(
                    "CHECK_FAILED requires a failure message"
            );
        }
        if (status != Status.CHECK_FAILED && failureMessage.isPresent()) {
            throw new IllegalArgumentException(
                    status + " cannot carry a failure message"
            );
        }
    }

    public static UpdateCheckResult upToDate(ApplicationVersion currentVersion) {
        return new UpdateCheckResult(
                Status.UP_TO_DATE,
                currentVersion,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static UpdateCheckResult updateAvailable(
            ApplicationVersion currentVersion,
            UpdateManifest manifest
    ) {
        return new UpdateCheckResult(
                Status.UPDATE_AVAILABLE,
                currentVersion,
                Optional.of(manifest),
                Optional.empty()
        );
    }

    public static UpdateCheckResult failed(
            ApplicationVersion currentVersion,
            String message
    ) {
        String normalized = message == null || message.isBlank()
                ? "Update check failed"
                : message.trim();
        return new UpdateCheckResult(
                Status.CHECK_FAILED,
                currentVersion,
                Optional.empty(),
                Optional.of(normalized)
        );
    }
}
