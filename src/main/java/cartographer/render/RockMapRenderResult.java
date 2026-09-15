package cartographer.render;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

public record RockMapRenderResult(
        BufferedImage image,
        MapViewportGeometry geometry,
        List<RockLegendEntry> legend,
        long observedCount,
        long noRockCount,
        long unavailableCount
) {
    public RockMapRenderResult {
        Objects.requireNonNull(image, "rock map image is required");
        Objects.requireNonNull(geometry, "rock map geometry is required");
        if (geometry.imageWidth() != image.getWidth()
                || geometry.imageHeight() != image.getHeight()) {
            throw new IllegalArgumentException("rock map geometry dimensions must match image");
        }
        legend = List.copyOf(
                Objects.requireNonNull(legend, "rock map legend is required")
        );
        if (observedCount < 0 || noRockCount < 0 || unavailableCount < 0) {
            throw new IllegalArgumentException("rock map counts must not be negative");
        }
    }
}
