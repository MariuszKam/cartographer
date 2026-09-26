package cartographer.application;

import cartographer.coverage.RegionCoverageSummary;
import cartographer.render.MapViewportGeometry;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

public record RenderCoverageMapResult(
        BufferedImage image,
        Optional<MapViewportGeometry> geometry,
        RegionCoverageSummary summary,
        ReadDiagnostics mapRegionDiagnostics
) {
    public RenderCoverageMapResult {
        Objects.requireNonNull(image, "rendered image is required");
        Objects.requireNonNull(geometry, "coverage geometry is required");
        Objects.requireNonNull(summary, "coverage summary is required");
        Objects.requireNonNull(mapRegionDiagnostics, "mapregion diagnostics are required");
    }
}
