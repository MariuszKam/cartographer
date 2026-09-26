package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationStatusBar extends HBox {
    private final Label operation = new Label("Ready");
    private final ProgressBar progress = new ProgressBar();
    private final Label progressText = new Label();
    private final Button cancel = new Button("Cancel");
    private final Label zoom = telemetry("Zoom 100%");
    private final Label radius = telemetry("Radius 256");
    private final Label mapScale = telemetry("Map —");
    private final Label cursor = telemetry("Cursor —");

    public WorkstationStatusBar() {
        super(10);
        getStyleClass().addAll("status-bar", "operation-bar");
        setAlignment(Pos.CENTER_LEFT);

        operation.getStyleClass().add("operation-status");
        progress.getStyleClass().add("status-progress");
        progress.setPrefWidth(170);
        progress.setMaxWidth(170);
        progress.setVisible(false);
        progress.setManaged(false);

        progressText.getStyleClass().add("status-progress-text");
        progressText.setVisible(false);
        progressText.setManaged(false);

        cancel.getStyleClass().add("cancel-button");
        cancel.setVisible(false);
        cancel.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
                operation,
                progress,
                progressText,
                cancel,
                spacer,
                zoom,
                radius,
                mapScale,
                cursor
        );
    }

    private Label telemetry(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("telemetry-pill");
        return label;
    }

    public void setStatus(String text) {
        operation.setText(text == null || text.isBlank() ? "Ready" : text);
    }


    public void setOperationActive(boolean active, boolean cancellable) {
        if (active) {
            setIndeterminateProgress();
        }
        progress.setVisible(active);
        progress.setManaged(active);
        progressText.setVisible(active);
        progressText.setManaged(active);
        cancel.setVisible(active && cancellable);
        cancel.setManaged(active && cancellable);
        cancel.setDisable(!active || !cancellable);
        operation.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("busy"),
                active
        );
    }

    public void setOnCancel(Runnable action) {
        cancel.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
    }

    public void setIndeterminateProgress() {
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressText.setText("Working…");
    }

    public void setProgress(double completed, double total) {
        if (total <= 0.0
                || !Double.isFinite(completed)
                || !Double.isFinite(total)) {
            setIndeterminateProgress();
            return;
        }
        double fraction = Math.clamp(completed / total, 0.0, 1.0);
        progress.setProgress(fraction);
        progressText.setText(Math.round(fraction * 100.0) + "%");
    }

    public void setZoomFactor(double factor) {
        zoom.setText("Zoom " + Math.round(factor * 100) + "%");
    }

    public void setRadius(int value) {
        radius.setText("R" + value);
    }

    public void setRadiusVisible(boolean visible) {
        radius.setVisible(visible);
        radius.setManaged(visible);
    }

    public void setMapGeometry(Optional<MapViewportGeometry> geometry) {
        Objects.requireNonNull(geometry, "geometry is required");
        mapScale.setText(
                geometry.map(MapScaleSummary::format)
                        .orElse("Map —")
        );
    }

    public void clearMapGeometry() {
        mapScale.setText("Map —");
    }

    public void setCursorCoordinates(double displayX, double displayZ) {
        cursor.setText(String.format(
                Locale.ROOT,
                "X %.1f  Z %.1f",
                displayX,
                displayZ
        ));
    }

    public void clearCursorCoordinates() {
        cursor.setText("Cursor —");
    }
}
