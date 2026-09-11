package cartographer.render;

import java.util.Set;

public record RenderOptions(int radiusBlocks, int pixelsPerBlock, RenderStyle style, Set<RenderLayer> layers) {
    public RenderOptions {
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("radiusBlocks must be positive");
        }
        if (pixelsPerBlock <= 0) {
            throw new IllegalArgumentException("pixelsPerBlock must be positive");
        }
        layers = Set.copyOf(layers);
    }
}
