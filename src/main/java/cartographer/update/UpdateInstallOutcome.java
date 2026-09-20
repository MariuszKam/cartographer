package cartographer.update;

import java.util.Objects;

public record UpdateInstallOutcome(
        Status status,
        ApplicationVersion version,
        Reason reason,
        int exitCode
) {

    public enum Status {
        SUCCESS,
        FAILED
    }

    public enum Reason {
        SUCCESS,
        INTEGRITY_CHECK_FAILED,
        INSTALLER_FAILED,
        BOOTSTRAP_FAILED
    }

    public UpdateInstallOutcome {
        status = Objects.requireNonNull(status, "status is required");
        version = Objects.requireNonNull(version, "version is required");
        reason = Objects.requireNonNull(reason, "reason is required");

        if (status == Status.SUCCESS) {
            if (reason != Reason.SUCCESS || exitCode != 0) {
                throw new IllegalArgumentException(
                        "Successful update outcome requires SUCCESS reason "
                                + "and exit code 0"
                );
            }
        } else if (reason == Reason.SUCCESS) {
            throw new IllegalArgumentException(
                    "Failed update outcome cannot use SUCCESS reason"
            );
        }
    }
}
