package cartographer.ui.workstation;

import cartographer.update.ApplicationVersion;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.util.Objects;

public final class WorkstationWorldBar extends HBox {
    private final Label tool = new Label("ORES");
    private final Label version = new Label("v—");
    private final Label save = new Label("No save");
    private final Label player = new Label("Player —");
    private final Button checkUpdates = new Button("Check updates");
    private final Button updateAvailable = new Button();
    private final Button downloadUpdate = new Button("Download");
    private final WorldSnapshotPane snapshotPane;
    private Runnable onCheckForUpdates = () -> { };
    private Runnable onOpenUpdateRelease = () -> { };
    private Runnable onDownloadUpdate = () -> { };

    public WorkstationWorldBar(
            WorldPanel worldPanel,
            Runnable onPrepareWorld
    ) {
        super(12);
        Objects.requireNonNull(worldPanel, "worldPanel is required");
        snapshotPane = new WorldSnapshotPane(
                Objects.requireNonNull(
                        onPrepareWorld,
                        "onPrepareWorld is required"
                )
        );
        getStyleClass().add("world-bar");
        setAlignment(Pos.CENTER_LEFT);

        Label brand = new Label("VS CARTOGRAPHER");
        brand.getStyleClass().add("brand-title");
        version.getStyleClass().add("app-version");
        tool.getStyleClass().add("active-tool-chip");
        save.getStyleClass().add("world-summary");
        player.getStyleClass().add("world-summary");
        checkUpdates.getStyleClass().add("update-check-button");
        updateAvailable.getStyleClass().add("update-available-chip");
        downloadUpdate.getStyleClass().add("update-check-button");
        updateAvailable.setVisible(false);
        updateAvailable.setManaged(false);
        downloadUpdate.setVisible(false);
        downloadUpdate.setManaged(false);
        checkUpdates.setOnAction(event -> onCheckForUpdates.run());
        updateAvailable.setOnAction(event -> onOpenUpdateRelease.run());
        downloadUpdate.setOnAction(event -> onDownloadUpdate.run());

        Region spacer = new Region();
        HBox.setHgrow(worldPanel, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        worldPanel.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(
                brand,
                version,
                tool,
                worldPanel,
                spacer,
                snapshotPane,
                updateAvailable,
                downloadUpdate,
                checkUpdates,
                save,
                player
        );
    }

    public void setCurrentVersion(ApplicationVersion currentVersion) {
        version.setText("v" + Objects.requireNonNull(
                currentVersion,
                "currentVersion is required"
        ));
    }

    public void setOnCheckForUpdates(Runnable action) {
        onCheckForUpdates = action == null ? () -> { } : action;
    }

    public void setOnOpenUpdateRelease(Runnable action) {
        onOpenUpdateRelease = action == null ? () -> { } : action;
    }

    public void setOnDownloadUpdate(Runnable action) {
        onDownloadUpdate = action == null ? () -> { } : action;
    }

    public void showUpdateChecking() {
        checkUpdates.setDisable(true);
        checkUpdates.setText("Checking…");
        checkUpdates.setTooltip(null);
        downloadUpdate.setDisable(true);
    }

    public void showUpdateAvailable(ApplicationVersion availableVersion) {
        ApplicationVersion checkedVersion = Objects.requireNonNull(
                availableVersion,
                "availableVersion is required"
        );
        updateAvailable.setText("Update " + checkedVersion);
        updateAvailable.setVisible(true);
        updateAvailable.setManaged(true);
        updateAvailable.setDisable(false);
        updateAvailable.setTooltip(
                new Tooltip("Open release notes on GitHub")
        );

        downloadUpdate.setText("Download");
        downloadUpdate.setVisible(true);
        downloadUpdate.setManaged(true);
        downloadUpdate.setDisable(false);
        downloadUpdate.setTooltip(
                new Tooltip("Download and verify the installer")
        );

        checkUpdates.setDisable(false);
        checkUpdates.setText("Check again");
        checkUpdates.setTooltip(null);
    }

    public void showUpdateDownloading(
            ApplicationVersion availableVersion,
            int percent
    ) {
        Objects.requireNonNull(
                availableVersion,
                "availableVersion is required"
        );
        int boundedPercent = Math.max(0, Math.min(100, percent));
        downloadUpdate.setVisible(true);
        downloadUpdate.setManaged(true);
        downloadUpdate.setDisable(true);
        downloadUpdate.setText("Downloading " + boundedPercent + "%");
        downloadUpdate.setTooltip(
                new Tooltip("Downloading and verifying update")
        );
        checkUpdates.setDisable(true);
    }

    public void showUpdateReady(ApplicationVersion availableVersion) {
        ApplicationVersion checkedVersion = Objects.requireNonNull(
                availableVersion,
                "availableVersion is required"
        );
        downloadUpdate.setVisible(true);
        downloadUpdate.setManaged(true);
        downloadUpdate.setDisable(true);
        downloadUpdate.setText("Ready " + checkedVersion);
        downloadUpdate.setTooltip(
                new Tooltip(
                        "Verified installer is ready; installation is Stage 4"
                )
        );
        checkUpdates.setDisable(false);
        checkUpdates.setText("Check again");
        checkUpdates.setTooltip(null);
    }

    public void showUpdateDownloadFailed(
            ApplicationVersion availableVersion,
            String message
    ) {
        Objects.requireNonNull(
                availableVersion,
                "availableVersion is required"
        );
        downloadUpdate.setVisible(true);
        downloadUpdate.setManaged(true);
        downloadUpdate.setDisable(false);
        downloadUpdate.setText("Retry download");
        downloadUpdate.setTooltip(
                new Tooltip(
                        message == null || message.isBlank()
                                ? "Update download failed"
                                : message
                )
        );
        checkUpdates.setDisable(false);
    }

    public void showUpToDate() {
        updateAvailable.setVisible(false);
        updateAvailable.setManaged(false);
        downloadUpdate.setVisible(false);
        downloadUpdate.setManaged(false);
        checkUpdates.setDisable(false);
        checkUpdates.setText("Up to date");
        checkUpdates.setTooltip(
                new Tooltip("Click to check again")
        );
    }

    public void showUpdateCheckFailed(String message) {
        checkUpdates.setDisable(false);
        checkUpdates.setText("Retry updates");
        checkUpdates.setTooltip(
                new Tooltip(
                        message == null || message.isBlank()
                                ? "Update check failed"
                                : message
                )
        );
        if (updateAvailable.isVisible()) {
            downloadUpdate.setDisable(false);
        }
    }

    public void setSnapshotSaveAvailable(boolean available) {
        snapshotPane.setSaveAvailable(available);
    }

    public void setSnapshotSourceBusy(boolean busy) {
        snapshotPane.setSourceBusy(busy);
    }

    public void setSnapshotPreparing(boolean preparing) {
        snapshotPane.setPreparing(preparing);
    }

    public void setSnapshotStatus(
            cartographer.application.WorldSnapshotStatus status
    ) {
        snapshotPane.showStatus(status);
    }

    public void setSavePath(Path path) {
        save.setText(path == null ? "No save" : path.getFileName().toString());
    }

    public void setPlayerLoaded(boolean loaded) {
        player.setText(loaded ? "Player loaded" : "Player —");
        player.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("available"),
                loaded
        );
    }

    public void setTool(WorkstationTool mode) {
        tool.setText(switch (Objects.requireNonNull(mode, "mode is required")) {
            case MAP -> "MAP";
            case COVERAGE -> "COVERAGE";
            case ORE -> "ORES";
            case SURFACE -> "SURFACE";
            case GEOLOGY -> "GEOLOGY";
            case PROSPECTING -> "PROSPECTING";
        });
    }
}
