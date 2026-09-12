package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.scanner.ActualBlockYFilter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public record RenderActualOreMapRequest(
        Path savePath,
        int radius,
        int pixelsPerBlock,
        RenderStyle style,
        Set<RenderLayer> layers,
        Optional<String> oreMatch,
        ActualBlockYFilter yFilter,
        Optional<WorldPosition> center
) {

    public RenderActualOreMapRequest {
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
        oreMatch = Optional.ofNullable(oreMatch).orElse(Optional.empty());
        yFilter = yFilter == null ? ActualBlockYFilter.unbounded() : yFilter;
        center = Optional.ofNullable(center).orElse(Optional.empty());

        if (oreMatch.isPresent() && oreMatch.orElseThrow().isBlank()) {
            throw new IllegalArgumentException("oreMatch must not be blank");
        }
    }
}
