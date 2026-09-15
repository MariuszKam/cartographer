package cartographer.coverage;

import cartographer.render.MapViewportGeometry;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

public record RegionCoverageRenderResult(
        BufferedImage image,
        Optional<MapViewportGeometry> geometry
) {
    public RegionCoverageRenderResult {
        Objects.requireNonNull(image, "coverage image is required");
        geometry = Objects.requireNonNull(geometry, "coverage geometry is required");
        geometry.ifPresent(value -> {
            if (value.imageWidth() != image.getWidth()
                    || value.imageHeight() != image.getHeight()) {
                throw new IllegalArgumentException(
                        "coverage geometry dimensions must match image"
                );
            }
        });
    }
}
