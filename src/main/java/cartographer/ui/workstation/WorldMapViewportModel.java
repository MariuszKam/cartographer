package cartographer.ui.workstation;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLod;
import cartographer.render.RenderTileBounds;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * JavaFX-independent camera and tile-demand model for the progressive viewport.
 */
public final class WorldMapViewportModel {
    private static final double MIN_PIXELS_PER_BLOCK = 0.03125;
    private static final double MAX_PIXELS_PER_BLOCK = 16.0;

    private final RenderTileLayout layout;
    private double centerWorldX;
    private double centerWorldZ;
    private double pixelsPerBlock = 1.0;
    private double viewportWidth;
    private double viewportHeight;

    public WorldMapViewportModel(RenderTileLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout is required");
    }

    public void setViewportSize(double width, double height) {
        if (!Double.isFinite(width)
                || !Double.isFinite(height)
                || width < 0.0
                || height < 0.0) {
            throw new IllegalArgumentException(
                    "viewport dimensions must be finite and non-negative"
            );
        }
        viewportWidth = width;
        viewportHeight = height;
    }

    public void centerOn(WorldPosition position) {
        Objects.requireNonNull(position, "position is required");
        centerWorldX = position.x();
        centerWorldZ = position.z();
    }

    public void centerOn(double worldX, double worldZ) {
        requireFinite(worldX, "worldX");
        requireFinite(worldZ, "worldZ");
        centerWorldX = worldX;
        centerWorldZ = worldZ;
    }

    public void panByPixels(double deltaX, double deltaY) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaY, "deltaY");
        centerWorldX -= deltaX / pixelsPerBlock;
        centerWorldZ -= deltaY / pixelsPerBlock;
    }

    public void zoomAt(
            double factor,
            double viewportX,
            double viewportY
    ) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException(
                    "zoom factor must be positive and finite"
            );
        }
        MapCursorPosition anchorBefore = worldAt(viewportX, viewportY);
        pixelsPerBlock = Math.clamp(
                pixelsPerBlock * factor,
                MIN_PIXELS_PER_BLOCK,
                MAX_PIXELS_PER_BLOCK
        );
        MapCursorPosition anchorAfter = worldAt(viewportX, viewportY);
        centerWorldX += anchorBefore.absoluteX() - anchorAfter.absoluteX();
        centerWorldZ += anchorBefore.absoluteZ() - anchorAfter.absoluteZ();
    }

    public boolean zoomAtWithinTileLimit(
            double factor,
            double viewportX,
            double viewportY,
            int prefetchTileMargin,
            int maxDemandedTiles
    ) {
        if (maxDemandedTiles <= 0) {
            throw new IllegalArgumentException(
                    "maxDemandedTiles must be positive"
            );
        }
        WorldMapViewportDemand before = demand(prefetchTileMargin);
        double previousCenterWorldX = centerWorldX;
        double previousCenterWorldZ = centerWorldZ;
        double previousPixelsPerBlock = pixelsPerBlock;

        zoomAt(factor, viewportX, viewportY);

        WorldMapViewportDemand after = demand(prefetchTileMargin);
        int beforeCount = demandCount(before);
        int afterCount = demandCount(after);
        if (afterCount <= maxDemandedTiles || afterCount < beforeCount) {
            return Double.compare(
                    previousPixelsPerBlock,
                    pixelsPerBlock
            ) != 0;
        }

        centerWorldX = previousCenterWorldX;
        centerWorldZ = previousCenterWorldZ;
        pixelsPerBlock = previousPixelsPerBlock;
        return false;
    }

    public MapCursorPosition worldAt(double viewportX, double viewportY) {
        requireFinite(viewportX, "viewportX");
        requireFinite(viewportY, "viewportY");
        return new MapCursorPosition(
                centerWorldX
                        + (viewportX - viewportWidth / 2.0)
                        / pixelsPerBlock,
                centerWorldZ
                        + (viewportY - viewportHeight / 2.0)
                        / pixelsPerBlock
        );
    }

    public ViewportPoint viewportAt(double worldX, double worldZ) {
        requireFinite(worldX, "worldX");
        requireFinite(worldZ, "worldZ");
        return new ViewportPoint(
                viewportWidth / 2.0
                        + (worldX - centerWorldX) * pixelsPerBlock,
                viewportHeight / 2.0
                        + (worldZ - centerWorldZ) * pixelsPerBlock
        );
    }

    public WorldMapViewportDemand demand(int prefetchTileMargin) {
        if (prefetchTileMargin < 0) {
            throw new IllegalArgumentException(
                    "prefetchTileMargin must not be negative"
            );
        }
        if (viewportWidth <= 0.0 || viewportHeight <= 0.0) {
            return new WorldMapViewportDemand(
                    List.of(),
                    List.of(),
                    currentLod()
            );
        }

        List<RenderTileCoordinate> visible = visibleTiles();
        if (prefetchTileMargin == 0 || visible.isEmpty()) {
            return new WorldMapViewportDemand(
                    visible,
                    List.of(),
                    currentLod()
            );
        }

        Set<RenderTileCoordinate> visibleSet =
                new LinkedHashSet<>(visible);
        int minX = visible.stream()
                .mapToInt(RenderTileCoordinate::x)
                .min()
                .orElseThrow();
        int maxX = visible.stream()
                .mapToInt(RenderTileCoordinate::x)
                .max()
                .orElseThrow();
        int minZ = visible.stream()
                .mapToInt(RenderTileCoordinate::z)
                .min()
                .orElseThrow();
        int maxZ = visible.stream()
                .mapToInt(RenderTileCoordinate::z)
                .max()
                .orElseThrow();

        List<RenderTileCoordinate> prefetch = new ArrayList<>();
        for (long z = (long) minZ - prefetchTileMargin;
             z <= (long) maxZ + prefetchTileMargin;
             z++) {
            for (long x = (long) minX - prefetchTileMargin;
                 x <= (long) maxX + prefetchTileMargin;
                 x++) {
                if (x < Integer.MIN_VALUE
                        || x > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE
                        || z > Integer.MAX_VALUE) {
                    continue;
                }
                RenderTileCoordinate coordinate =
                        new RenderTileCoordinate((int) x, (int) z);
                if (!visibleSet.contains(coordinate)) {
                    prefetch.add(coordinate);
                }
            }
        }
        return new WorldMapViewportDemand(
                visible,
                prefetch,
                currentLod()
        );
    }

    public void fit(RenderTileBounds bounds, double paddingPixels) {
        Objects.requireNonNull(bounds, "bounds is required");
        if (!Double.isFinite(paddingPixels) || paddingPixels < 0.0) {
            throw new IllegalArgumentException(
                    "paddingPixels must be finite and non-negative"
            );
        }
        if (viewportWidth <= paddingPixels * 2.0
                || viewportHeight <= paddingPixels * 2.0) {
            return;
        }

        centerWorldX = (bounds.worldMinX()
                + bounds.worldMaxXExclusive()) / 2.0;
        centerWorldZ = (bounds.worldMinZ()
                + bounds.worldMaxZExclusive()) / 2.0;
        double availableWidth = viewportWidth - paddingPixels * 2.0;
        double availableHeight = viewportHeight - paddingPixels * 2.0;
        pixelsPerBlock = Math.clamp(
                Math.min(
                        availableWidth / bounds.widthBlocks(),
                        availableHeight / bounds.heightBlocks()
                ),
                MIN_PIXELS_PER_BLOCK,
                MAX_PIXELS_PER_BLOCK
        );
    }

    public double pixelsPerBlock() {
        return pixelsPerBlock;
    }

    public RenderLod currentLod() {
        return RenderLod.forPixelsPerBlock(pixelsPerBlock);
    }

    public double centerWorldX() {
        return centerWorldX;
    }

    public double centerWorldZ() {
        return centerWorldZ;
    }

    private List<RenderTileCoordinate> visibleTiles() {
        MapCursorPosition topLeft = worldAt(0.0, 0.0);
        MapCursorPosition bottomRight = worldAt(
                viewportWidth,
                viewportHeight
        );

        RenderTileCoordinate first = layout.coordinateForWorld(
                topLeft.absoluteX(),
                topLeft.absoluteZ()
        );
        RenderTileCoordinate last = layout.coordinateForWorld(
                Math.nextDown(bottomRight.absoluteX()),
                Math.nextDown(bottomRight.absoluteZ())
        );

        List<RenderTileCoordinate> coordinates = new ArrayList<>();
        for (long z = first.z(); z <= last.z(); z++) {
            for (long x = first.x(); x <= last.x(); x++) {
                coordinates.add(
                        new RenderTileCoordinate((int) x, (int) z)
                );
            }
        }
        return List.copyOf(coordinates);
    }

    private static int demandCount(WorldMapViewportDemand demand) {
        return Math.addExact(
                demand.visible().size(),
                demand.prefetch().size()
        );
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite"
            );
        }
    }

    public record ViewportPoint(double x, double y) {
        public ViewportPoint {
            requireFinite(x, "x");
            requireFinite(y, "y");
        }
    }
}
