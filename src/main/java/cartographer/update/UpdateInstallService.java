package cartographer.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class UpdateInstallService implements UpdateInstallerLauncher {
    private final UpdateInstallerVerifier verifier;
    private final UpdateBootstrapper bootstrapper;

    public UpdateInstallService(
            UpdateInstallerVerifier verifier,
            UpdateBootstrapper bootstrapper
    ) {
        this.verifier = Objects.requireNonNull(
                verifier,
                "verifier is required"
        );
        this.bootstrapper = Objects.requireNonNull(
                bootstrapper,
                "bootstrapper is required"
        );
    }

    @Override
    public UpdateInstallLaunchResult launch(
            UpdateDownloadResult readyUpdate
    ) {
        Objects.requireNonNull(
                readyUpdate,
                "readyUpdate is required"
        );
        if (readyUpdate.status() != UpdateDownloadResult.Status.READY) {
            return UpdateInstallLaunchResult.failed(
                    "Update installer is not ready"
            );
        }

        Path installer = readyUpdate.installerPath().orElseThrow();
        try {
            if (!verifier.isVerified(
                    installer,
                    readyUpdate.manifest()
            )) {
                return UpdateInstallLaunchResult.invalidInstaller(
                        "Verified installer is missing or no longer matches "
                                + "the release manifest"
                );
            }
        } catch (IOException exception) {
            return UpdateInstallLaunchResult.invalidInstaller(
                    "Verified installer cannot be re-validated: "
                            + conciseMessage(exception)
            );
        }

        try {
            bootstrapper.launch(
                    readyUpdate.manifest(),
                    installer
            );
            return UpdateInstallLaunchResult.started();
        } catch (Exception exception) {
            return UpdateInstallLaunchResult.failed(
                    "Cannot start update installer bootstrap: "
                            + conciseMessage(exception)
            );
        }
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
