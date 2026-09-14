package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResource;
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
        SurfaceResourceSelection selection,
        Optional<WorldPosition> center
) {

    public RenderSurfaceResourceMapRequest(
            Path savePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Set<RenderLayer> layers,
            SurfaceResourceMatch match,
            Optional<WorldPosition> center
    ) {
        this(savePath, radius, pixelsPerBlock, style, layers,
                SurfaceResourceSelection.legacy(match), center);
    }

    public static RenderSurfaceResourceMapRequest forObservedResource(
            Path savePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Set<RenderLayer> layers,
            ObservedSurfaceResource resource,
            WorldPosition center
    ) {
        return new RenderSurfaceResourceMapRequest(
                savePath,
                radius,
                pixelsPerBlock,
                style,
                layers,
                SurfaceResourceSelection.observed(resource),
                Optional.of(center)
        );
    }

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
        if (selection == null) {
            throw new NullPointerException("selection is required");
        }
        center = Optional.ofNullable(center).orElse(Optional.empty());
    }

    public Optional<SurfaceResourceMatch> legacyMatch() {
        return selection.legacyMatch();
    }

    /**
     * Legacy accessor retained for callers that construct legacy requests.
     * Observed-resource requests intentionally have no matcher.
     */
    public SurfaceResourceMatch match() {
        return legacyMatch().orElseThrow(
                () -> new IllegalStateException(
                        "Observed-resource requests do not expose a legacy match"
                )
        );
    }

    public Optional<ObservedSurfaceResource> observedResource() {
        return selection.observedResource();
    }

    public String resourceDisplayName() {
        return selection.displayName();
    }
}
