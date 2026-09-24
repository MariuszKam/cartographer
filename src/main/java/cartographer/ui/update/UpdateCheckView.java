package cartographer.ui.update;

import cartographer.update.ApplicationVersion;

public interface UpdateCheckView {
    void showCurrentVersion(ApplicationVersion version);

    void setOnCheckForUpdates(Runnable action);

    void setOnOpenUpdateRelease(Runnable action);

    void setOnDownloadUpdate(Runnable action);

    void setOnInstallUpdate(Runnable action);

    void showUpdateChecking();

    void showUpdateAvailable(ApplicationVersion version);

    void showUpdateDownloading(
            ApplicationVersion version,
            int percent
    );

    void showUpdateReady(ApplicationVersion version);

    void showUpdateDownloadFailed(
            ApplicationVersion version,
            String message
    );

    void showUpdateInstallLaunching(ApplicationVersion version);

    void showUpdateInstallFailed(
            ApplicationVersion version,
            String message
    );

    void showUpdateInstalled(ApplicationVersion version);

    void showUpdateRestartRequired(ApplicationVersion version);

    void showPreviousUpdateInstallFailed(
            ApplicationVersion version,
            String message
    );

    void showUpToDate();

    void showUpdateCheckFailed(String message);
}
