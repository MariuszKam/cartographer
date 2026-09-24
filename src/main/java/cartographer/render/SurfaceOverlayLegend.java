package cartographer.render;

import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceRenderAnalysis;

import java.util.List;

/** Typed, renderer-independent labels for the two surface overlay modes. */
public record SurfaceOverlayLegend(
        String title,
        List<String> metrics,
        boolean depositCenters
) {
    public SurfaceOverlayLegend {
        title = title == null ? "" : title;
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public static SurfaceOverlayLegend forAnalysis(SurfaceRenderAnalysis analysis) {
        SurfaceMaterialAnalysis material = (SurfaceMaterialAnalysis) analysis;
        return new SurfaceOverlayLegend(
                "Surface material: " + material.materialName(),
                List.of("Matched blocks: " + material.matchedBlockCount(),
                        "Areas/deposits: " + material.depositCount()),
                true
        );
    }

}
