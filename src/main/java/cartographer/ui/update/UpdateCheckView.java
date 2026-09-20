package cartographer.ui.update;

import cartographer.update.ApplicationVersion;

public interface UpdateCheckView {
    void showCurrentVersion(ApplicationVersion version);

    void setOnCheckForUpdates(Runnable action);

    void setOnOpenUpdateRelease(Runnable action);

    void showUpdateChecking();

    void showUpdateAvailable(ApplicationVersion version);

    void showUpToDate();

    void showUpdateCheckFailed(String message);
}
