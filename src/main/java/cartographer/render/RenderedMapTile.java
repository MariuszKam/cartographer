package cartographer.render;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** One independently placeable progressive map raster tile. */
public record RenderedMapTile(
        RenderTileCoordinate coordinate,
        RenderTileBounds worldBounds,
        BufferedImage image,
        RenderLod lod,
        int terrainPixelsDrawn,
        RenderStyle style
) {
    public RenderedMapTile {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(worldBounds, "worldBounds is required");
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(lod, "lod is required");
        Objects.requireNonNull(style, "style is required");
        if (terrainPixelsDrawn < 0) {
            throw new IllegalArgumentException(
                    "terrainPixelsDrawn must not be negative"
            );
        }

        long expectedWidth = divideCeil(
                worldBounds.widthBlocks(),
                lod.blocksPerPixel()
        );
        long expectedHeight = divideCeil(
                worldBounds.heightBlocks(),
                lod.blocksPerPixel()
        );
        if (image.getWidth() != expectedWidth
                || image.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(
                    "image dimensions do not match tile world bounds"
            );
        }
    }

    public RenderedMapTile(
            RenderTileCoordinate coordinate,
            RenderTileBounds worldBounds,
            BufferedImage image,
            int blocksPerPixel,
            int terrainPixelsDrawn,
            RenderStyle style
    ) {
        this(
                coordinate,
                worldBounds,
                image,
                RenderLod.forBlocksPerPixel(blocksPerPixel),
                terrainPixelsDrawn,
                style
        );
    }

    public int blocksPerPixel() {
        return lod.blocksPerPixel();
    }

    private static long divideCeil(long value, int divisor) {
        return Math.floorDiv(
                Math.addExact(value, divisor - 1L),
                divisor
        );
    }
}
