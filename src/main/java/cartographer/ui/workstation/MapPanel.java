package cartographer.ui.workstation;

import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class MapPanel extends BorderPane {
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double ZOOM_STEP = 1.25;

    private final ImageView imageView = new ImageView();
    private final StackPane mapContent = new StackPane(imageView);
    private final ScrollPane preview = new ScrollPane(mapContent);
    private final MapToolbar toolbar;
    private double zoomFactor = 1.0;
    private double baseWidth;
    private double baseHeight;
    private boolean mapAvailable;
    private Consumer<Double> zoomListener = ignored -> { };

    public MapPanel() {
        getStyleClass().add("map-viewport");
        toolbar = new MapToolbar(this::zoomOut, this::zoomIn, this::fit, this::centerPlayer, this::resetView);
        preview.setPannable(true);
        preview.setFitToWidth(false);
        preview.setFitToHeight(false);
        preview.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isControlDown() && mapAvailable) {
                if (event.getDeltaY() > 0) zoomIn();
                if (event.getDeltaY() < 0) zoomOut();
                event.consume();
            }
        });
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        setTop(toolbar);
        setCenter(preview);
    }

    public void show(BufferedImage image) {
        show(SwingFXUtils.toFXImage(image, null), image.getWidth(), image.getHeight());
    }

    public double zoomFactor() { return zoomFactor; }
    public void setOnZoomChanged(Consumer<Double> listener) { zoomListener = listener == null ? ignored -> { } : listener; zoomListener.accept(zoomFactor); }

    private void show(Image image, int width, int height) {
        imageView.setImage(image);
        baseWidth = width;
        baseHeight = height;
        mapAvailable = true;
        toolbar.setMapAvailable(true);
        resetView();
    }

    public void zoomIn() { setZoom(zoomFactor * ZOOM_STEP); }

    public void zoomOut() { setZoom(zoomFactor / ZOOM_STEP); }

    public void fit() {
        if (!mapAvailable) return;
        double viewportWidth = preview.getViewportBounds().getWidth();
        double viewportHeight = preview.getViewportBounds().getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) return;
        setZoom(Math.min(viewportWidth / baseWidth, viewportHeight / baseHeight));
        centerView();
    }

    public void resetView() {
        if (!mapAvailable) return;
        setZoom(1.0);
        centerView();
    }

    public void centerPlayer() {
        // Player pixel metadata is not currently part of the render result.
    }

    private void setZoom(double requested) {
        if (!mapAvailable) return;
        zoomFactor = Math.clamp(requested, MIN_ZOOM, MAX_ZOOM);
        imageView.setFitWidth(baseWidth * zoomFactor);
        imageView.setFitHeight(baseHeight * zoomFactor);
        zoomListener.accept(zoomFactor);
    }

    private void centerView() {
        preview.setHvalue(0.5);
        preview.setVvalue(0.5);
    }

}
