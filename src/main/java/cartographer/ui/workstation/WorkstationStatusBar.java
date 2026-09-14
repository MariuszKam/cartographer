package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

public final class WorkstationStatusBar extends HBox {
    private final Label operation = new Label("Ready");
    private final Label zoom = new Label("Zoom 100%");
    private final Label radius = new Label("Radius 256");
    private final ProgressIndicator progress = new ProgressIndicator();

    public WorkstationStatusBar() {
        super(12);
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        progress.setMinSize(18, 18);
        progress.setPrefSize(18, 18);
        progress.setMaxSize(18, 18);
        progress.setVisible(false);
        progress.setManaged(false);
        getChildren().addAll(operation, progress, zoom, radius);
    }

    public void setStatus(String text) {
        operation.setText(text == null || text.isBlank() ? "Ready" : text);
    }

    public void setBusy(boolean busy) {
        progress.setVisible(busy);
        progress.setManaged(busy);
    }

    public void setZoomFactor(double factor) {
        zoom.setText("Zoom " + Math.round(factor * 100) + "%");
    }

    public void setRadius(int value) {
        radius.setText("Radius " + value);
    }
}
