package cartographer.render;

import java.util.Arrays;

/** Progressive raster level-of-detail for one fixed world-space render tile. */
public enum RenderLod {
    LOD_0(0, 1),
    LOD_1(1, 2),
    LOD_2(2, 4),
    LOD_3(3, 8),
    LOD_4(4, 16),
    LOD_5(5, 32);

    private final int level;
    private final int blocksPerPixel;

    RenderLod(int level, int blocksPerPixel) {
        this.level = level;
        this.blocksPerPixel = blocksPerPixel;
    }

    public int level() {
        return level;
    }

    public int blocksPerPixel() {
        return blocksPerPixel;
    }

    public static RenderLod fullDetail() {
        return LOD_0;
    }

    public static RenderLod forBlocksPerPixel(int blocksPerPixel) {
        return Arrays.stream(values())
                .filter(lod -> lod.blocksPerPixel == blocksPerPixel)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "unsupported blocksPerPixel: " + blocksPerPixel
                        )
                );
    }

    /**
     * Chooses the closest power-of-two source sampling density for the current
     * display scale. The selected raster never depends on total world extent.
     */
    public static RenderLod forPixelsPerBlock(double pixelsPerBlock) {
        if (!Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0.0) {
            throw new IllegalArgumentException(
                    "pixelsPerBlock must be positive and finite"
            );
        }

        double desiredBlocksPerPixel = 1.0 / pixelsPerBlock;
        RenderLod best = LOD_0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (RenderLod candidate : values()) {
            double distance = Math.abs(
                    Math.log(
                            candidate.blocksPerPixel
                                    / desiredBlocksPerPixel
                    ) / Math.log(2.0)
            );
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
