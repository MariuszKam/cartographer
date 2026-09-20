package cartographer.update;

import java.util.Objects;

public final class UpdateCheckService {
    private final ApplicationVersion currentVersion;
    private final UpdateManifestSource manifestSource;
    private final UpdateManifestParser manifestParser;

    public UpdateCheckService(
            ApplicationVersion currentVersion,
            UpdateManifestSource manifestSource,
            UpdateManifestParser manifestParser
    ) {
        this.currentVersion = Objects.requireNonNull(
                currentVersion,
                "currentVersion is required"
        );
        this.manifestSource = Objects.requireNonNull(
                manifestSource,
                "manifestSource is required"
        );
        this.manifestParser = Objects.requireNonNull(
                manifestParser,
                "manifestParser is required"
        );
    }

    public ApplicationVersion currentVersion() {
        return currentVersion;
    }

    public UpdateCheckResult check() {
        try {
            UpdateManifest manifest = manifestParser.parse(
                    manifestSource.load()
            );
            if (manifest.version().compareTo(currentVersion) > 0) {
                return UpdateCheckResult.updateAvailable(
                        currentVersion,
                        manifest
                );
            }
            return UpdateCheckResult.upToDate(currentVersion);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return UpdateCheckResult.failed(
                    currentVersion,
                    "Update check was interrupted"
            );
        } catch (Exception exception) {
            return UpdateCheckResult.failed(
                    currentVersion,
                    conciseMessage(exception)
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
