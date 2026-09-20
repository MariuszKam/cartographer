package cartographer.ui.workstation;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.util.Objects;

public final class WorkstationWorldBar extends HBox {
    private final Label tool = new Label("ORES");
    private final Label save = new Label("No save");
    private final Label player = new Label("Player —");
    private final WorldSnapshotPane snapshotPane;

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
        tool.getStyleClass().add("active-tool-chip");
        save.getStyleClass().add("world-summary");
        player.getStyleClass().add("world-summary");

        Region spacer = new Region();
        HBox.setHgrow(worldPanel, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        worldPanel.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(
                brand,
                tool,
                worldPanel,
                spacer,
                snapshotPane,
                save,
                player
        );
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
