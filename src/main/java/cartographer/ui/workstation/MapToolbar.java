package cartographer.ui.workstation;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public final class MapToolbar extends HBox {
    private final Button zoomOut = new Button("-");
    private final Button zoomIn = new Button("+");
    private final Button fit = new Button("Fit");
    private final Button centerPlayer = new Button("Center Player");
    private final Button reset = new Button("Reset");
    private boolean mapAvailable;
    private boolean centerPlayerAvailable;

    public MapToolbar(Runnable onZoomOut, Runnable onZoomIn, Runnable onFit, Runnable onCenterPlayer, Runnable onReset) {
        super(4);
        getStyleClass().add("map-toolbar");
        zoomOut.setTooltip(new Tooltip("Zoom out"));
        zoomIn.setTooltip(new Tooltip("Zoom in"));
        fit.setTooltip(new Tooltip("Fit the raster to the viewport"));
        centerPlayer.setTooltip(new Tooltip("Center the viewport on the player"));
        reset.setTooltip(new Tooltip("Reset to 100% zoom"));
        zoomOut.getStyleClass().add("map-toolbar-button");
        zoomIn.getStyleClass().add("map-toolbar-button");
        fit.getStyleClass().add("map-toolbar-button");
        centerPlayer.getStyleClass().add("map-toolbar-button");
        reset.getStyleClass().add("map-toolbar-button");
        getChildren().addAll(zoomOut, zoomIn, fit, centerPlayer, reset);
        zoomOut.setOnAction(event -> onZoomOut.run());
        zoomIn.setOnAction(event -> onZoomIn.run());
        fit.setOnAction(event -> onFit.run());
        centerPlayer.setOnAction(event -> onCenterPlayer.run());
        reset.setOnAction(event -> onReset.run());
        setMapAvailable(false);
    }

    public void setMapAvailable(boolean available) {
        mapAvailable = available;
        zoomOut.setDisable(!available);
        zoomIn.setDisable(!available);
        fit.setDisable(!available);
        reset.setDisable(!available);
        updateCenterPlayerAvailability();
    }

    public void setCenterPlayerAvailable(boolean available) {
        centerPlayerAvailable = available;
        updateCenterPlayerAvailability();
    }

    private void updateCenterPlayerAvailability() {
        centerPlayer.setDisable(!mapAvailable || !centerPlayerAvailable);
    }
}
