package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.List;

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
            SurfaceMaterialMatch match,
            Optional<WorldPosition> center
    ) {
        this(savePath, radius, pixelsPerBlock, style, layers,
                SurfaceResourceSelection.material(match), center);
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
        return forObservedResources(
                savePath,
                radius,
                pixelsPerBlock,
                style,
                layers,
                List.of(resource), center
        );
    }

    public static RenderSurfaceResourceMapRequest forObservedResources(
            Path savePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Set<RenderLayer> layers,
            List<ObservedSurfaceResource> resources,
            WorldPosition center
    ) {
        return new RenderSurfaceResourceMapRequest(
                savePath, radius, pixelsPerBlock, style, layers,
                SurfaceResourceSelection.observedResources(resources),
                Optional.of(center));
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

    public Optional<SurfaceMaterialMatch> material() {
        return selection.material();
    }

    /** Returns the material matcher for a material request. */
    public SurfaceMaterialMatch materialMatch() {
        return material().orElseThrow(() -> new IllegalStateException(
                "Observed-resource requests do not expose a material match"));
    }

    public List<ObservedSurfaceResource> observedResources() {
        return selection.observedResources();
    }

    public String resourceDisplayName() {
        return selection.displayName();
    }
}
