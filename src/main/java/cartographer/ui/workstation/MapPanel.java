package cartographer.ui.workstation;

import cartographer.model.WorldPosition;
import cartographer.render.MapViewportGeometry;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.embed.swing.SwingFXUtils;
import java.util.Objects;
import java.util.Optional;
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
    private Optional<MapViewportGeometry> geometry = Optional.empty();
    private Optional<WorldPosition> player = Optional.empty();
    private Consumer<Double> zoomListener = ignored -> { };
    private Consumer<Optional<MapCursorPosition>> cursorListener = ignored -> { };

    public MapPanel() {
        getStyleClass().add("map-viewport");
        toolbar = new MapToolbar(this::zoomOut, this::zoomIn, this::fit, this::centerPlayer, this::resetView);
        preview.setPannable(true);
        preview.setFitToWidth(false);
        preview.setFitToHeight(false);
        mapContent.setAlignment(Pos.CENTER);
        preview.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                mapContent.setMinSize(bounds.getWidth(), bounds.getHeight()));
        preview.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isControlDown() && mapAvailable) {
                if (event.getDeltaY() > 0) zoomIn();
                if (event.getDeltaY() < 0) zoomOut();
                event.consume();
            }
        });
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setPickOnBounds(true);
        imageView.setOnMouseMoved(event -> {
            Bounds imageBounds = imageView.getBoundsInLocal();
            publishCursorPosition(MapCursorMapping.toAbsoluteWorld(
                    geometry.orElse(null),
                    event.getX(),
                    event.getY(),
                    imageBounds.getMinX(),
                    imageBounds.getMinY(),
                    imageBounds.getWidth(),
                    imageBounds.getHeight()
            ));
        });
        imageView.setOnMouseExited(event ->
                publishCursorPosition(Optional.empty()));

        StackPane viewportChrome = new StackPane(preview, toolbar);
        viewportChrome.getStyleClass().add("map-viewport-chrome");
        StackPane.setAlignment(toolbar, Pos.TOP_LEFT);
        StackPane.setMargin(toolbar, new Insets(12));
        toolbar.setMaxSize(
                javafx.scene.layout.Region.USE_PREF_SIZE,
                javafx.scene.layout.Region.USE_PREF_SIZE
        );
        setCenter(viewportChrome);
    }

    public void show(
            BufferedImage image,
            Optional<MapViewportGeometry> geometry,
            Optional<WorldPosition> player
    ) {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(player, "player is required");
        geometry.ifPresent(value -> {
            if (value.imageWidth() != image.getWidth()
                    || value.imageHeight() != image.getHeight()) {
                throw new IllegalArgumentException(
                        "map geometry dimensions must match image"
                );
            }
        });
        publishCursorPosition(Optional.empty());
        this.geometry = geometry;
        this.player = player;
        show(SwingFXUtils.toFXImage(image, null), image.getWidth(), image.getHeight());
        updateCenterPlayerAvailability();
    }

    public void replaceImage(
            BufferedImage image,
            Optional<MapViewportGeometry> geometry,
            Optional<WorldPosition> player
    ) {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(player, "player is required");
        geometry.ifPresent(value -> {
            if (value.imageWidth() != image.getWidth()
                    || value.imageHeight() != image.getHeight()) {
                throw new IllegalArgumentException(
                        "map geometry dimensions must match image"
                );
            }
        });
        publishCursorPosition(Optional.empty());
        this.geometry = geometry;
        this.player = player;
        imageView.setImage(SwingFXUtils.toFXImage(image, null));
        baseWidth = image.getWidth();
        baseHeight = image.getHeight();
        mapAvailable = true;
        toolbar.setMapAvailable(true);
        imageView.setFitWidth(baseWidth * zoomFactor);
        imageView.setFitHeight(baseHeight * zoomFactor);
        updateCenterPlayerAvailability();
    }

    public void setOnZoomChanged(Consumer<Double> listener) { zoomListener = listener == null ? ignored -> { } : listener; zoomListener.accept(zoomFactor); }
    public void setOnCursorPositionChanged(Consumer<Optional<MapCursorPosition>> listener) {
        cursorListener = listener == null ? ignored -> { } : listener;
        publishCursorPosition(Optional.empty());
    }

    private void show(Image image, int width, int height) {
        imageView.setImage(image);
        baseWidth = width;
        baseHeight = height;
        mapAvailable = true;
        toolbar.setMapAvailable(true);
        updateCenterPlayerAvailability();
        Platform.runLater(this::fit);
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
        if (!mapAvailable || geometry.isEmpty() || player.isEmpty()) {
            return;
        }
        MapViewportGeometry currentGeometry = geometry.orElseThrow();
        WorldPosition currentPlayer = player.orElseThrow();
        if (!currentGeometry.containsAbsoluteWorldPoint(
                currentPlayer.x(), currentPlayer.z()
        )) {
            return;
        }
        Platform.runLater(this::centerPlayerAfterLayout);
    }

    public void clearNavigationContext() {
        geometry = Optional.empty();
        player = Optional.empty();
        publishCursorPosition(Optional.empty());
        updateCenterPlayerAvailability();
    }

    private void publishCursorPosition(Optional<MapCursorPosition> position) {
        cursorListener.accept(Objects.requireNonNull(position, "cursor position is required"));
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

    private void updateCenterPlayerAvailability() {
        boolean available = mapAvailable
                && geometry.isPresent()
                && player.isPresent()
                && geometry.orElseThrow().containsAbsoluteWorldPoint(
                        player.orElseThrow().x(),
                        player.orElseThrow().z()
                );
        toolbar.setCenterPlayerAvailable(available);
    }

    private void centerPlayerAfterLayout() {
        if (!mapAvailable || geometry.isEmpty() || player.isEmpty()) {
            return;
        }
        Bounds imageBounds = imageView.getLayoutBounds();
        Bounds contentBounds = mapContent.getLayoutBounds();
        Bounds viewportBounds = preview.getViewportBounds();
        MapViewportGeometry currentGeometry = geometry.orElseThrow();
        WorldPosition currentPlayer = player.orElseThrow();
        if (imageBounds.getWidth() <= 0.0
                || imageBounds.getHeight() <= 0.0
                || !currentGeometry.containsAbsoluteWorldPoint(
                currentPlayer.x(), currentPlayer.z()
        )) {
            return;
        }

        double sourceX = currentGeometry.absoluteWorldXToImageX(currentPlayer.x());
        double sourceY = currentGeometry.absoluteWorldZToImageY(currentPlayer.z());
        double imageLocalX = imageBounds.getMinX()
                + sourceX / currentGeometry.imageWidth() * imageBounds.getWidth();
        double imageLocalY = imageBounds.getMinY()
                + sourceY / currentGeometry.imageHeight() * imageBounds.getHeight();
        Point2D contentPoint = imageView.localToParent(imageLocalX, imageLocalY);

        double horizontalFraction = MapViewportNavigation.centeredScrollFraction(
                contentBounds.getMinX(),
                contentBounds.getWidth(),
                viewportBounds.getWidth(),
                contentPoint.getX()
        );
        double verticalFraction = MapViewportNavigation.centeredScrollFraction(
                contentBounds.getMinY(),
                contentBounds.getHeight(),
                viewportBounds.getHeight(),
                contentPoint.getY()
        );
        preview.setHvalue(MapViewportNavigation.interpolateScrollValue(
                preview.getHmin(), preview.getHmax(), horizontalFraction
        ));
        preview.setVvalue(MapViewportNavigation.interpolateScrollValue(
                preview.getVmin(), preview.getVmax(), verticalFraction
        ));
    }

}
