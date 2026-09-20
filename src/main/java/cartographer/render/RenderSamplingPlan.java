package cartographer.render;

import cartographer.model.WorldPosition;

import java.util.Objects;

/**
 * Immutable request-shaped sampling contract for the main square map raster.
 *
 * <p>The plan centralizes the exact mapping that the renderer already uses
 * between raster pixels and absolute world columns. Future render-sized
 * Terrain, Surface and ROCK consumers can therefore request only the world
 * samples that the raster can observe without changing the current image
 * semantics.</p>
 *
 * <p>Axis lookup tables are intentionally bounded by
 * {@link MapRasterContract#MAX_RASTER_SIZE}; they replace repeated floating
 * point division in pixel hot loops and make the sampling decision explicit.</p>
 */
public final class RenderSamplingPlan {
    private final MapRasterContract rasterContract;
    private final MapViewportGeometry geometry;
    private final int worldMinX;
    private final int worldMinZ;
    private final int[] worldXByImageColumn;
    private final int[] worldZByImageRow;

    private RenderSamplingPlan(
            MapRasterContract rasterContract,
            MapViewportGeometry geometry,
            int worldMinX,
            int worldMinZ,
            int[] worldXByImageColumn,
            int[] worldZByImageRow
    ) {
        this.rasterContract = rasterContract;
        this.geometry = geometry;
        this.worldMinX = worldMinX;
        this.worldMinZ = worldMinZ;
        this.worldXByImageColumn = worldXByImageColumn;
        this.worldZByImageRow = worldZByImageRow;
    }

    public static RenderSamplingPlan from(
            WorldPosition center,
            RenderOptions options
    ) {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(options, "render options are required");

        MapRasterContract rasterContract = MapRasterContract.from(options);
        int worldMinX = (int) Math.floor(center.x()) - options.radiusBlocks();
        int worldMinZ = (int) Math.floor(center.z()) - options.radiusBlocks();
        int worldDiameter = rasterContract.worldDiameterBlocks();
        int rasterSize = rasterContract.rasterSize();
        double scale = rasterContract.effectivePixelsPerBlock();

        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                rasterSize,
                rasterSize,
                worldMinX,
                worldMinZ,
                worldMinX + (double) worldDiameter,
                worldMinZ + (double) worldDiameter
        );

        return new RenderSamplingPlan(
                rasterContract,
                geometry,
                worldMinX,
                worldMinZ,
                buildAxisSamples(worldMinX, worldDiameter, rasterSize, scale),
                buildAxisSamples(worldMinZ, worldDiameter, rasterSize, scale)
        );
    }

    public int rasterSize() {
        return rasterContract.rasterSize();
    }

    public int worldDiameterBlocks() {
        return rasterContract.worldDiameterBlocks();
    }

    public int worldMinX() {
        return worldMinX;
    }

    public int worldMinZ() {
        return worldMinZ;
    }

    public double effectivePixelsPerBlock() {
        return rasterContract.effectivePixelsPerBlock();
    }

    public double effectiveBlocksPerPixel() {
        return rasterContract.effectiveBlocksPerPixel();
    }

    public boolean capped() {
        return rasterContract.capped();
    }

    public MapViewportGeometry geometry() {
        return geometry;
    }

    public int worldXForImageColumn(int imageX) {
        return worldXByImageColumn[Objects.checkIndex(imageX, worldXByImageColumn.length)];
    }

    public int worldZForImageRow(int imageY) {
        return worldZByImageRow[Objects.checkIndex(imageY, worldZByImageRow.length)];
    }

    private static int[] buildAxisSamples(
            int worldMin,
            int worldDiameter,
            int rasterSize,
            double effectivePixelsPerBlock
    ) {
        int[] samples = new int[rasterSize];
        for (int imageCoordinate = 0;
             imageCoordinate < rasterSize;
             imageCoordinate++) {
            samples[imageCoordinate] =
                    worldMin
                            + Math.min(
                            worldDiameter - 1,
                            (int) Math.floor(
                                    imageCoordinate / effectivePixelsPerBlock
                            )
                    );
        }
        return samples;
    }
}
