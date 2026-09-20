package cartographer.ui.workstation;

import cartographer.application.WorldSnapshotStatus;
import cartographer.perf.WorldSnapshotPreparationSummary;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.Objects;

public final class WorldSnapshotPane extends HBox {
    private static final PseudoClass READY =
            PseudoClass.getPseudoClass("ready");
    private static final PseudoClass PARTIAL =
            PseudoClass.getPseudoClass("partial");

    private final Label state = new Label("Snapshot: no save");
    private final Label revision = new Label("rev —");
    private final Label coverage = new Label("Coverage —");
    private final Button prepare = new Button("Prepare world");

    private boolean saveAvailable;
    private boolean sourceBusy;
    private boolean preparing;

    public WorldSnapshotPane(Runnable onPrepare) {
        super(7);
        Objects.requireNonNull(onPrepare, "onPrepare is required");
        getStyleClass().add("world-snapshot-pane");
        setAlignment(Pos.CENTER_LEFT);

        state.getStyleClass().add("snapshot-state");
        revision.getStyleClass().add("snapshot-revision");
        coverage.getStyleClass().add("snapshot-coverage");
        prepare.getStyleClass().add("snapshot-prepare-button");
        prepare.setTooltip(new Tooltip(
                "Build or repair reusable PF-2 world data for the current save revision."
        ));
        prepare.setOnAction(event -> onPrepare.run());

        getChildren().addAll(
                state,
                revision,
                coverage,
                prepare
        );
        refreshButton();
    }

    public void setSaveAvailable(boolean available) {
        saveAvailable = available;
        if (!available) {
            state.setText("Snapshot: no save");
            revision.setText("rev —");
            coverage.setText("Coverage —");
            state.pseudoClassStateChanged(READY, false);
            state.pseudoClassStateChanged(PARTIAL, false);
        }
        refreshButton();
    }

    public void setSourceBusy(boolean busy) {
        sourceBusy = busy;
        refreshButton();
    }

    public void setPreparing(boolean preparing) {
        this.preparing = preparing;
        if (preparing) {
            state.setText("Snapshot: preparing…");
            coverage.setText("Indexing reusable world data");
            state.pseudoClassStateChanged(READY, false);
            state.pseudoClassStateChanged(PARTIAL, true);
        }
        refreshButton();
    }

    public void showStatus(WorldSnapshotStatus status) {
        Objects.requireNonNull(status, "status is required");
        revision.setText("rev " + status.shortRevision());
        state.pseudoClassStateChanged(
                READY,
                status.state() == WorldSnapshotStatus.State.READY
        );
        state.pseudoClassStateChanged(
                PARTIAL,
                status.state() == WorldSnapshotStatus.State.PARTIAL
        );

        switch (status.state()) {
            case NOT_PREPARED -> {
                state.setText("Snapshot: not prepared");
                coverage.setText("Coverage —");
                prepare.setText("Prepare world");
            }
            case PARTIAL -> {
                state.setText("Snapshot: partial");
                coverage.setText(
                        status.summary()
                                .map(this::coverageText)
                                .orElse("Resumable derived data")
                );
                prepare.setText("Resume prepare");
            }
            case READY -> {
                state.setText("Snapshot: ready");
                coverage.setText(
                        status.summary()
                                .map(this::coverageText)
                                .orElse("Coverage complete")
                );
                prepare.setText("Refresh snapshot");
            }
        }
        preparing = false;
        refreshButton();
    }

    private String coverageText(
            WorldSnapshotPreparationSummary summary
    ) {
        return "T" + mark(summary.terrainCoverageComplete())
                + " S" + mark(summary.surfaceCoverageComplete())
                + " Rg" + mark(summary.mapRegionCoverageComplete())
                + " Rock" + mark(summary.upperRockCoverageComplete())
                + " Ore" + mark(summary.resourceIndexCoverageComplete())
                + " · " + summary.observedMapChunks() + " mapchunks";
    }

    private String mark(boolean complete) {
        return complete ? "✓" : "…";
    }

    private void refreshButton() {
        prepare.setDisable(
                !saveAvailable || sourceBusy || preparing
        );
    }
}
