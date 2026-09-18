package cartographer.render;

import java.util.Objects;

/**
 * Current square-raster contract for the main map renderer.
 *
 * <p>Analysis/world coverage and raster resolution are intentionally separate:
 * large radii may represent more than one world block per output pixel. This is
 * not LOD or incremental rendering; it is the bounded raster contract used by
 * the current renderer.</p>
 */
public record MapRasterContract(
        int radiusBlocks,
        int requestedPixelsPerBlock,
        int worldDiameterBlocks,
        long requestedRasterSize,
        int rasterSize,
        double effectivePixelsPerBlock,
        double effectiveBlocksPerPixel,
        boolean capped
) {
    public static final int MIN_RASTER_SIZE = 64;
    public static final int MAX_RASTER_SIZE = 4096;

    public MapRasterContract {
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("radiusBlocks must be positive");
        }
        if (requestedPixelsPerBlock <= 0) {
            throw new IllegalArgumentException("requestedPixelsPerBlock must be positive");
        }
        if (worldDiameterBlocks <= 0) {
            throw new IllegalArgumentException("worldDiameterBlocks must be positive");
        }
        if (requestedRasterSize <= 0 || rasterSize <= 0) {
            throw new IllegalArgumentException("raster sizes must be positive");
        }
        if (!Double.isFinite(effectivePixelsPerBlock)
                || effectivePixelsPerBlock <= 0.0
                || !Double.isFinite(effectiveBlocksPerPixel)
                || effectiveBlocksPerPixel <= 0.0) {
            throw new IllegalArgumentException("effective scale must be positive and finite");
        }
    }

    public static MapRasterContract from(RenderOptions options) {
        Objects.requireNonNull(options, "render options are required");
        int worldDiameter = Math.multiplyExact(options.radiusBlocks(), 2);
        long requested = Math.addExact(
                Math.multiplyExact(
                        (long) worldDiameter,
                        options.pixelsPerBlock()
                ),
                1L
        );
        int raster = Math.toIntExact(Math.clamp(
                requested,
                MIN_RASTER_SIZE,
                MAX_RASTER_SIZE
        ));
        double effectivePixelsPerBlock = raster / (double) worldDiameter;
        double effectiveBlocksPerPixel = worldDiameter / (double) raster;
        return new MapRasterContract(
                options.radiusBlocks(),
                options.pixelsPerBlock(),
                worldDiameter,
                requested,
                raster,
                effectivePixelsPerBlock,
                effectiveBlocksPerPixel,
                requested > MAX_RASTER_SIZE
        );
    }
}
