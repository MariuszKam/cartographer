package cartographer.ui.workstation;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLod;
import cartographer.render.RenderTileBounds;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import cartographer.render.RenderTileLayout;
import cartographer.render.RenderedMapTile;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Virtualized progressive world-map viewport backed by one screen-sized Canvas.
 */
public final class WorldMapViewport extends Region {
    private static final double ZOOM_STEP = 1.25;
    private static final int PREFETCH_TILE_MARGIN = 1;

    private final WorldMapViewportModel model;
    private final Canvas canvas = new Canvas();
    private final int maxCachedTiles;
    private final Map<RenderTileKey, CachedTile> tiles;
    private final AtomicBoolean redrawPending = new AtomicBoolean();

    private Consumer<WorldMapViewportDemand> demandListener =
            ignored -> { };
    private Consumer<Optional<MapCursorPosition>> cursorListener =
            ignored -> { };
    private List<WorldMapMarker> markers = List.of();
    private double dragX;
    private double dragY;
    private boolean dragging;

    public WorldMapViewport(
            RenderTileLayout layout,
            int maxCachedTiles
    ) {
        Objects.requireNonNull(layout, "layout is required");
        if (maxCachedTiles <= 0) {
            throw new IllegalArgumentException(
                    "maxCachedTiles must be positive"
            );
        }
        this.maxCachedTiles = maxCachedTiles;
        this.model = new WorldMapViewportModel(layout);
        this.tiles = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<RenderTileKey, CachedTile> eldest
            ) {
                return size() > WorldMapViewport.this.maxCachedTiles;
            }
        };

        getChildren().add(canvas);
        setMinSize(0, 0);

        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragging = true;
                dragX = event.getX();
                dragY = event.getY();
            }
        });
        setOnMouseDragged(event -> {
            if (!dragging) {
                return;
            }
            double nextX = event.getX();
            double nextY = event.getY();
            model.panByPixels(nextX - dragX, nextY - dragY);
            dragX = nextX;
            dragY = nextY;
            requestRedrawAndDemand();
        });
        setOnMouseReleased(event -> dragging = false);
        setOnMouseMoved(event -> cursorListener.accept(
                Optional.of(model.worldAt(event.getX(), event.getY()))
        ));
        setOnMouseExited(event -> cursorListener.accept(Optional.empty()));
        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0.0) {
                return;
            }
            model.zoomAt(
                    event.getDeltaY() > 0.0
                            ? ZOOM_STEP
                            : 1.0 / ZOOM_STEP,
                    event.getX(),
                    event.getY()
            );
            requestRedrawAndDemand();
            event.consume();
        });
    }

    public void setOnTileDemand(
            Consumer<WorldMapViewportDemand> listener
    ) {
        demandListener = listener == null ? ignored -> { } : listener;
        publishDemand();
    }

    public void setOnCursorPositionChanged(
            Consumer<Optional<MapCursorPosition>> listener
    ) {
        cursorListener = listener == null ? ignored -> { } : listener;
    }

    public void setMarkers(List<WorldMapMarker> markers) {
        Objects.requireNonNull(markers, "markers are required");
        if (!Platform.isFxApplicationThread()) {
            List<WorldMapMarker> copy = List.copyOf(markers);
            Platform.runLater(() -> setMarkers(copy));
            return;
        }
        this.markers = List.copyOf(markers);
        requestRedraw();
    }

    public void centerOn(WorldPosition position) {
        model.centerOn(Objects.requireNonNull(position, "position is required"));
        requestRedrawAndDemand();
    }

    public void acceptTile(RenderedMapTile tile) {
        Objects.requireNonNull(tile, "tile is required");
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> acceptTile(tile));
            return;
        }
        tiles.put(
                new RenderTileKey(
                        tile.coordinate(),
                        tile.lod()
                ),
                new CachedTile(
                        tile.worldBounds(),
                        SwingFXUtils.toFXImage(tile.image(), null)
                )
        );
        requestRedraw();
    }

    public void clearTiles() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::clearTiles);
            return;
        }
        tiles.clear();
        requestRedraw();
    }

    public RenderLod currentLod() {
        return model.currentLod();
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(Math.max(0.0, width));
        canvas.setHeight(Math.max(0.0, height));
        model.setViewportSize(
                canvas.getWidth(),
                canvas.getHeight()
        );
        redraw();
        publishDemand();
    }

    @Override
    protected double computePrefWidth(double height) {
        return 640.0;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 480.0;
    }

    private void requestRedrawAndDemand() {
        requestRedraw();
        publishDemand();
    }

    private void requestRedraw() {
        if (redrawPending.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                redrawPending.set(false);
                redraw();
            });
        }
    }

    private void redraw() {
        if (!Platform.isFxApplicationThread()) {
            requestRedraw();
            return;
        }
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        WorldMapViewportDemand demand = model.demand(0);
        for (RenderTileCoordinate coordinate : demand.visible()) {
            CachedTile tile = cachedTileFor(
                    new RenderTileKey(coordinate, demand.lod())
            );
            if (tile == null) {
                continue;
            }
            WorldMapViewportModel.ViewportPoint topLeft = model.viewportAt(
                    tile.bounds().worldMinX(),
                    tile.bounds().worldMinZ()
            );
            double width = tile.bounds().widthBlocks()
                    * model.pixelsPerBlock();
            double height = tile.bounds().heightBlocks()
                    * model.pixelsPerBlock();
            graphics.drawImage(
                    tile.image(),
                    topLeft.x(),
                    topLeft.y(),
                    width,
                    height
            );
        }
        drawMarkers(graphics);
    }

    private CachedTile cachedTileFor(RenderTileKey desired) {
        CachedTile exact = tiles.get(desired);
        if (exact != null) {
            return exact;
        }

        RenderTileKey bestKey = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RenderTileKey candidate : tiles.keySet()) {
            if (!candidate.coordinate().equals(desired.coordinate())) {
                continue;
            }
            int distance = Math.abs(
                    candidate.lod().level() - desired.lod().level()
            );
            if (distance < bestDistance) {
                bestKey = candidate;
                bestDistance = distance;
            }
        }
        return bestKey == null ? null : tiles.get(bestKey);
    }

    private void drawMarkers(GraphicsContext graphics) {
        for (WorldMapMarker marker : markers) {
            WorldMapViewportModel.ViewportPoint point = model.viewportAt(
                    marker.position().x(),
                    marker.position().z()
            );
            if (point.x() < -24.0
                    || point.y() < -24.0
                    || point.x() > canvas.getWidth() + 24.0
                    || point.y() > canvas.getHeight() + 24.0) {
                continue;
            }

            Color color = switch (marker.kind()) {
                case PLAYER -> Color.WHITE;
                case HOME -> Color.GOLD;
                case USER -> Color.CYAN;
            };
            double radius = marker.kind() == WorldMapMarker.Kind.PLAYER
                    ? 6.0
                    : 5.0;
            graphics.setFill(color);
            graphics.fillOval(
                    point.x() - radius,
                    point.y() - radius,
                    radius * 2.0,
                    radius * 2.0
            );
            if (!marker.label().isBlank()) {
                graphics.fillText(
                        marker.label(),
                        point.x() + radius + 3.0,
                        point.y() - radius - 2.0
                );
            }
        }
    }

    private void publishDemand() {
        if (getWidth() <= 0.0 || getHeight() <= 0.0) {
            return;
        }
        demandListener.accept(
                model.demand(PREFETCH_TILE_MARGIN)
        );
    }

    private record CachedTile(
            RenderTileBounds bounds,
            Image image
    ) {
        private CachedTile {
            Objects.requireNonNull(bounds, "bounds is required");
            Objects.requireNonNull(image, "image is required");
        }
    }
}
