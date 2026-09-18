package cartographer.ui.workstation;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import cartographer.render.MapViewportGeometry;
import java.util.Locale;
import java.util.Optional;

public final class WorkstationStatusBar extends HBox {
    private final Label operation = new Label("Ready");
    private final ProgressBar progress = new ProgressBar();
    private final Label progressText = new Label();
    private final Button cancel = new Button("Cancel");
    private final Label zoom = new Label("Zoom 100%");
    private final Label radius = new Label("Radius 256");
    private final Label mapScale = new Label("Map —");
    private final Label cursor = new Label("Cursor —");

    public WorkstationStatusBar() {
        super(12);
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        progress.getStyleClass().add("status-progress");
        progress.setPrefWidth(160);
        progress.setMaxWidth(160);
        progress.setVisible(false);
        progress.setManaged(false);
        progressText.getStyleClass().add("status-progress-text");
        progressText.setVisible(false);
        progressText.setManaged(false);
        cancel.setVisible(false);
        cancel.setManaged(false);
        getChildren().addAll(
                operation,
                progress,
                progressText,
                cancel,
                zoom,
                radius,
                mapScale,
                cursor
        );
    }

    public void setStatus(String text) {
        operation.setText(text == null || text.isBlank() ? "Ready" : text);
    }

    public void setBusy(boolean busy) {
        setOperationActive(busy, false);
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
        progressText.setText("Working...");
    }

    public void setProgress(double completed, double total) {
        if (total <= 0.0 || !Double.isFinite(completed) || !Double.isFinite(total)) {
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
        radius.setText("Radius " + value);
    }

    public void setRadiusVisible(boolean visible) {
        radius.setVisible(visible);
        radius.setManaged(visible);
    }

    public void setMapGeometry(Optional<MapViewportGeometry> geometry) {
        Optional<MapViewportGeometry> safe =
                geometry == null ? Optional.empty() : geometry;
        mapScale.setText(
                safe.map(MapScaleSummary::format)
                        .orElse("Map —")
        );
    }

    public void clearMapGeometry() {
        mapScale.setText("Map —");
    }

    public void setCursorCoordinates(double displayX, double displayZ) {
        cursor.setText(String.format(Locale.ROOT, "Cursor X %.1f  Z %.1f", displayX, displayZ));
    }

    public void clearCursorCoordinates() {
        cursor.setText("Cursor —");
    }
}
