package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public record RenderSurfaceResourceMapRequest(
        Path savePath,
        int radius,
        int pixelsPerBlock,
        RenderStyle style,
        Set<RenderLayer> layers,
        SurfaceResourceMatch match,
        Optional<WorldPosition> center
) {

    public RenderSurfaceResourceMapRequest {
        if (savePath == null) {
            throw new NullPointerException("savePath is required");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (pixelsPerBlock <= 0) {
            throw new IllegalArgumentException("pixelsPerBlock must be positive");
        }
        if (style == null) {
            throw new NullPointerException("style is required");
        }
        layers = Set.copyOf(layers);
        if (match == null) {
            throw new NullPointerException("match is required");
        }
        center = Optional.ofNullable(center).orElse(Optional.empty());
    }
}
