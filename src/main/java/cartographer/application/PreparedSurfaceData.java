package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderSamplingPlan;
import cartographer.render.SurfaceRenderData;
import cartographer.scanner.SurfaceDiagnosticsSummary;
import cartographer.scanner.SurfaceMapScanResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Prepared Surface state separated by consumer need.
 *
 * <p>Map/Ore rendering retains raster-bounded {@link SurfaceRenderData} and
 * lightweight diagnostics. Exact request-shaped {@link SurfaceMapScanResult}
 * is retained only for ANALYSIS consumers.</p>
 */
public record PreparedSurfaceData(
        SurfaceRenderData renderData,
        SurfaceDiagnosticsSummary diagnostics,
        Optional<SurfaceMapScanResult> analysis
) {
    public PreparedSurfaceData {
        Objects.requireNonNull(renderData, "Surface render data is required");
        Objects.requireNonNull(diagnostics, "Surface diagnostics are required");
        Objects.requireNonNull(
                analysis,
                "Surface analysis option is required"
        );
    }

    public static PreparedSurfaceData none(
            WorldPosition center,
            RenderOptions options
    ) {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(options, "options are required");
        return new PreparedSurfaceData(
                SurfaceRenderData.empty(
                        RenderSamplingPlan.from(center, options)
                ),
                SurfaceDiagnosticsSummary.empty(),
                Optional.empty()
        );
    }

    public static PreparedSurfaceData fromExact(
            SurfaceMapScanResult exact,
            WorldPosition center,
            RenderOptions options,
            SurfaceDataRequirement requirement
    ) {
        Objects.requireNonNull(exact, "exact Surface result is required");
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(options, "options are required");
        Objects.requireNonNull(
                requirement,
                "Surface data requirement is required"
        );
        if (!requirement.requiresSurface()) {
            throw new IllegalArgumentException(
                    "exact Surface data requires RENDER or ANALYSIS"
            );
        }

        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);
        SurfaceRenderData renderData =
                options.layers().contains(RenderLayer.SURFACE)
                        ? SurfaceRenderData.from(
                                exact.map(),
                                sampling
                        )
                        : SurfaceRenderData.empty(sampling);

        return new PreparedSurfaceData(
                renderData,
                SurfaceDiagnosticsSummary.from(exact),
                requirement.requiresAnalysis()
                        ? Optional.of(exact)
                        : Optional.empty()
        );
    }

    public static PreparedSurfaceData renderOnly(
            SurfaceRenderData renderData,
            SurfaceDiagnosticsSummary diagnostics
    ) {
        return new PreparedSurfaceData(
                renderData,
                diagnostics,
                Optional.empty()
        );
    }

    public SurfaceMapScanResult requireAnalysis() {
        return analysis.orElseThrow(() -> new IllegalStateException(
                "exact Surface analysis data was not prepared"
        ));
    }
}
