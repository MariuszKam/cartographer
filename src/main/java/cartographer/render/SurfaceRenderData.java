package cartographer.render;

import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.scanner.CachedSurfaceTileView;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceRegistryLookup;
import cartographer.scanner.SurfaceTile;
import cartographer.scanner.SurfaceTileLayout;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Raster-bounded Surface state for map painting.
 *
 * <p>The structure stores only the final Surface source/class selected for
 * each output pixel plus the final soil-fertility overlay tier. It deliberately
 * does not retain per-world-column Surface analysis data.</p>
 */
public final class SurfaceRenderData {
    private final int rasterSize;
    private final long[] surfaceSourceByPixel;
    private final byte[] surfaceClassByPixel;
    private final BitSet surfacePresent;
    private final long surfaceClassMask;

    private SurfaceRenderData(
            int rasterSize,
            long[] surfaceSourceByPixel,
            byte[] surfaceClassByPixel,
            BitSet surfacePresent,
            long surfaceClassMask
    ) {
        this.rasterSize = rasterSize;
        this.surfaceSourceByPixel = surfaceSourceByPixel;
        this.surfaceClassByPixel = surfaceClassByPixel;
        this.surfacePresent = surfacePresent;
        this.surfaceClassMask = surfaceClassMask;
    }

    public static Builder builder(
            RenderSamplingPlan sampling,
            SurfaceTileLayout layout,
            SurfaceRegistryLookup registry
    ) {
        return new Builder(sampling, layout, registry);
    }

    public static SurfaceRenderData from(
            SurfaceMap map,
            SurfaceRegistryLookup registry,
            RenderSamplingPlan sampling
    ) {
        Objects.requireNonNull(map, "Surface map is required");
        Builder builder = builder(
                sampling,
                map.layout(),
                registry
        );
        map.forEachResolvedCell(
                (worldX, worldZ, y, blockId, liquidId, surfaceClass) ->
                        builder.acceptResolved(
                                worldX,
                                worldZ,
                                blockId,
                                surfaceClass
                        )
        );
        return builder.finish();
    }

    public static SurfaceRenderData empty(RenderSamplingPlan sampling) {
        Objects.requireNonNull(sampling, "sampling plan is required");
        return new SurfaceRenderData(
                sampling.rasterSize(),
                new long[0],
                new byte[0],
                new BitSet(),
                0L
        );
    }

    public int rasterSize() {
        return rasterSize;
    }

    public boolean hasSurfaceAt(int imageX, int imageY) {
        int index = pixelIndex(imageX, imageY);
        return surfaceSourceByPixel.length != 0
                && surfacePresent.get(index);
    }

    public boolean isEmpty() {
        return surfaceSourceByPixel.length == 0
                || surfacePresent.isEmpty();
    }

    public int surfaceWorldXAt(int imageX, int imageY) {
        long packed = requiredSource(pixelIndex(imageX, imageY));
        return (int) (packed >> 32);
    }

    public int surfaceWorldZAt(int imageX, int imageY) {
        long packed = requiredSource(pixelIndex(imageX, imageY));
        return (int) packed;
    }

    public SurfaceClass surfaceClassAt(int imageX, int imageY) {
        int index = pixelIndex(imageX, imageY);
        if (!surfacePresent.get(index)) {
            throw new IllegalStateException(
                    "Surface pixel is absent at " + imageX + "," + imageY
            );
        }
        return SurfaceClassCode.decode(surfaceClassByPixel[index]);
    }

    public Set<SurfaceClass> surfaceClasses() {
        EnumSet<SurfaceClass> classes =
                EnumSet.noneOf(SurfaceClass.class);
        for (SurfaceClass value : SurfaceClass.values()) {
            if ((surfaceClassMask & (1L << value.ordinal())) != 0L) {
                classes.add(value);
            }
        }
        return Set.copyOf(classes);
    }

    long[] surfaceSourceByPixelView() {
        return surfaceSourceByPixel;
    }

    byte[] surfaceClassByPixelView() {
        return surfaceClassByPixel;
    }

    BitSet surfacePresentView() {
        return surfacePresent;
    }

    private long requiredSource(int index) {
        if (!surfacePresent.get(index)) {
            throw new IllegalStateException("Surface pixel is absent");
        }
        return surfaceSourceByPixel[index];
    }

    private int pixelIndex(int imageX, int imageY) {
        Objects.checkIndex(imageX, rasterSize);
        Objects.checkIndex(imageY, rasterSize);
        return imageY * rasterSize + imageX;
    }

    public static final class Builder {
        private final SurfaceTileLayout layout;
        private final SurfaceRegistryLookup registry;
        private final int rasterSize;
        private final double scale;
        private final int worldMinX;
        private final int worldMinZ;
        private final long[] surfaceSourceByPixel;
        private final byte[] surfaceClassByPixel;
        private final BitSet surfacePresent;
        private long surfaceClassMask;
        private boolean finished;

        private Builder(
                RenderSamplingPlan sampling,
                SurfaceTileLayout layout,
                SurfaceRegistryLookup registry
        ) {
            Objects.requireNonNull(
                    sampling,
                    "sampling plan is required"
            );
            this.layout = Objects.requireNonNull(
                    layout,
                    "Surface layout is required"
            );
            this.registry = Objects.requireNonNull(
                    registry,
                    "Surface registry is required"
            );
            this.rasterSize = sampling.rasterSize();
            this.scale = sampling.effectivePixelsPerBlock();
            this.worldMinX = sampling.worldMinX();
            this.worldMinZ = sampling.worldMinZ();
            int pixels = Math.multiplyExact(rasterSize, rasterSize);
            this.surfaceSourceByPixel = new long[pixels];
            this.surfaceClassByPixel = new byte[pixels];
            this.surfacePresent = new BitSet(pixels);
        }

        public void acceptCachedTile(CachedSurfaceTileView tile) {
            ensureMutable();
            Objects.requireNonNull(tile, "cached Surface tile is required");
            int tileX = tile.coordinate().x();
            int tileZ = tile.coordinate().z();
            layout.tileIndex(tileX, tileZ);
            if (tile.width() != layout.tileWidth(tileX)
                    || tile.height() != layout.tileHeight(tileZ)) {
                throw new IllegalArgumentException(
                        "cached Surface tile geometry does not match request world"
                );
            }

            for (int localZ = 0; localZ < tile.height(); localZ++) {
                int worldZ = layout.worldZForTileLocal(tileZ, localZ);
                for (int localX = 0; localX < tile.width(); localX++) {
                    int cellIndex = localZ * tile.width() + localX;
                    if ((tile.stateAtIndex(cellIndex) & SurfaceTile.RESOLVED)
                            == 0) {
                        continue;
                    }
                    int worldX = layout.worldXForTileLocal(tileX, localX);
                    acceptResolved(
                            worldX,
                            worldZ,
                            tile.blockIdAtIndex(cellIndex),
                            SurfaceClassCode.decode(
                                    tile.surfaceClassCodeAtIndex(cellIndex)
                            )
                    );
                }
            }
        }

        public void acceptResolved(
                int worldX,
                int worldZ,
                int blockId,
                SurfaceClass surfaceClass
        ) {
            ensureMutable();
            Objects.requireNonNull(
                    surfaceClass,
                    "surface class is required"
            );
            if (!layout.isActive(worldX, worldZ)) {
                return;
            }

            int startX = (int) Math.floor(
                    (worldX - (double) worldMinX) * scale
            );
            int endX = (int) Math.ceil(
                    (worldX + 1.0 - worldMinX) * scale
            );
            int startY = (int) Math.floor(
                    (worldZ - (double) worldMinZ) * scale
            );
            int endY = (int) Math.ceil(
                    (worldZ + 1.0 - worldMinZ) * scale
            );
            if (endX <= 0
                    || endY <= 0
                    || startX >= rasterSize
                    || startY >= rasterSize) {
                return;
            }

            startX = Math.max(0, startX);
            startY = Math.max(0, startY);
            endX = Math.min(rasterSize, endX);
            endY = Math.min(rasterSize, endY);
            if (startX >= endX || startY >= endY) {
                return;
            }

            long packedSource = pack(worldX, worldZ);
            byte classCode = SurfaceClassCode.encode(surfaceClass);
            surfaceClassMask |= 1L << surfaceClass.ordinal();

            for (int imageY = startY; imageY < endY; imageY++) {
                int rowStart = imageY * rasterSize + startX;
                int rowEnd = imageY * rasterSize + endX;
                Arrays.fill(
                        surfaceSourceByPixel,
                        rowStart,
                        rowEnd,
                        packedSource
                );
                Arrays.fill(
                        surfaceClassByPixel,
                        rowStart,
                        rowEnd,
                        classCode
                );
                surfacePresent.set(rowStart, rowEnd);
            }
        }

        public SurfaceRenderData finish() {
            ensureMutable();
            finished = true;
            return new SurfaceRenderData(
                    rasterSize,
                    surfaceSourceByPixel,
                    surfaceClassByPixel,
                    surfacePresent,
                    surfaceClassMask
            );
        }

        private void ensureMutable() {
            if (finished) {
                throw new IllegalStateException(
                        "Surface render-data builder is finished"
                );
            }
        }

        private static long pack(int worldX, int worldZ) {
            return ((long) worldX << 32)
                    | (worldZ & 0xFFFFFFFFL);
        }
    }
}
