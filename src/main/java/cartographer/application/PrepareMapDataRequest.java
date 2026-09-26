package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Request for reusable terrain/surface preparation before painting overlays. */
public record PrepareMapDataRequest(
        Path savePath,
        int radius,
        int pixelsPerBlock,
        RenderStyle style,
        Set<RenderLayer> layers,
        Optional<WorldPosition> center,
        SurfaceDataRequirement surfaceDataRequirement,
        boolean ignoreFoliage
) {
    public PrepareMapDataRequest(
            Path savePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Set<RenderLayer> layers,
            Optional<WorldPosition> center,
            SurfaceDataRequirement surfaceDataRequirement
    ) {
        this(
                savePath,
                radius,
                pixelsPerBlock,
                style,
                layers,
                center,
                surfaceDataRequirement,
                true
        );
    }

    public PrepareMapDataRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (pixelsPerBlock <= 0) {
            throw new IllegalArgumentException("pixelsPerBlock must be positive");
        }
        Objects.requireNonNull(style, "style is required");
        layers = Set.copyOf(Objects.requireNonNull(layers, "layers are required"));
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(
                surfaceDataRequirement,
                "surfaceDataRequirement is required"
        );
    }
}
