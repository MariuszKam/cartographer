package cartographer.update;

@FunctionalInterface
public interface UpdateInstallerLauncher {
    UpdateInstallLaunchResult launch(UpdateDownloadResult readyUpdate);
}
