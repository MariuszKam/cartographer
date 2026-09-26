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
        RESTART_REQUIRED,
        FAILED
    }

    public enum Reason {
        SUCCESS,
        RESTART_REQUIRED,
        INTEGRITY_CHECK_FAILED,
        INSTALLER_FAILED,
        BOOTSTRAP_FAILED
    }

    public UpdateInstallOutcome {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(version, "version is required");
        Objects.requireNonNull(reason, "reason is required");

        if (status == Status.SUCCESS) {
            if (reason != Reason.SUCCESS
                    || (exitCode != 0 && exitCode != 1641)) {
                throw new IllegalArgumentException(
                        "Successful update outcome requires SUCCESS reason "
                                + "and Windows Installer success exit code"
                );
            }
        } else if (status == Status.RESTART_REQUIRED) {
            if (reason != Reason.RESTART_REQUIRED || exitCode != 3010) {
                throw new IllegalArgumentException(
                        "Restart-required update outcome requires "
                                + "RESTART_REQUIRED reason and exit code 3010"
                );
            }
        } else if (reason == Reason.SUCCESS
                || reason == Reason.RESTART_REQUIRED) {
            throw new IllegalArgumentException(
                    "Failed update outcome cannot use a success reason"
            );
        }
    }
}
