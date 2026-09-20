package cartographer.update;

import java.util.Objects;
import java.util.Optional;

public record UpdateInstallLaunchResult(
        Status status,
        Optional<String> failureMessage
) {

    public enum Status {
        STARTED,
        INVALID_INSTALLER,
        FAILED
    }

    public UpdateInstallLaunchResult {
        status = Objects.requireNonNull(status, "status is required");
        failureMessage = Objects.requireNonNull(
                failureMessage,
                "failureMessage is required"
        );
        if (status == Status.STARTED && failureMessage.isPresent()) {
            throw new IllegalArgumentException(
                    "STARTED result cannot contain a failure message"
            );
        }
        if (status != Status.STARTED && failureMessage.isEmpty()) {
            throw new IllegalArgumentException(
                    "Failed launch result must contain a failure message"
            );
        }
    }

    public static UpdateInstallLaunchResult started() {
        return new UpdateInstallLaunchResult(
                Status.STARTED,
                Optional.empty()
        );
    }

    public static UpdateInstallLaunchResult invalidInstaller(String message) {
        return failed(Status.INVALID_INSTALLER, message);
    }

    public static UpdateInstallLaunchResult failed(String message) {
        return failed(Status.FAILED, message);
    }

    private static UpdateInstallLaunchResult failed(
            Status status,
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
        return new UpdateInstallLaunchResult(
                status,
                Optional.of(normalized)
        );
    }
}
