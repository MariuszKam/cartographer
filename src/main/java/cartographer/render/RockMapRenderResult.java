package cartographer.render;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

public record RockMapRenderResult(
        BufferedImage image,
        List<RockLegendEntry> legend,
        long observedCount,
        long noRockCount,
        long unavailableCount
) {
    public RockMapRenderResult {
        Objects.requireNonNull(image, "rock map image is required");
        legend = List.copyOf(
                Objects.requireNonNull(legend, "rock map legend is required")
        );
        if (observedCount < 0 || noRockCount < 0 || unavailableCount < 0) {
            throw new IllegalArgumentException("rock map counts must not be negative");
        }
    }
}
