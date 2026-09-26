package cartographer.update;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record UpdateDownloadResult(
        Status status,
        UpdateManifest manifest,
        Optional<Path> installerPath,
        Optional<String> failureMessage
) {

    public enum Status {
        READY,
        FAILED
    }

    public UpdateDownloadResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(manifest, "manifest is required");
        Objects.requireNonNull(
                installerPath,
                "installerPath is required"
        );
        Objects.requireNonNull(
                failureMessage,
                "failureMessage is required"
        );

        if (status == Status.READY) {
            if (installerPath.isEmpty() || failureMessage.isPresent()) {
                throw new IllegalArgumentException(
                        "READY download must contain only an installer path"
                );
            }
        } else if (installerPath.isPresent() || failureMessage.isEmpty()) {
            throw new IllegalArgumentException(
                    "FAILED download must contain only a failure message"
            );
        }
    }

    public static UpdateDownloadResult ready(
            UpdateManifest manifest,
            Path installerPath
    ) {
        return new UpdateDownloadResult(
                Status.READY,
                manifest,
                Optional.of(Objects.requireNonNull(
                        installerPath,
                        "installerPath is required"
                )),
                Optional.empty()
        );
    }

    public static UpdateDownloadResult failed(
            UpdateManifest manifest,
            String message
    ) {
        String normalized = Objects.requireNonNull(
                message,
                "message is required"
        ).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "failure message cannot be blank"
            );
        }
        return new UpdateDownloadResult(
                Status.FAILED,
                manifest,
                Optional.empty(),
                Optional.of(normalized)
        );
    }
}
