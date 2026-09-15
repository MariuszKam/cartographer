package cartographer.application;

import cartographer.coverage.RegionCoverageSummary;
import cartographer.save.ReadDiagnostics;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record RenderCoverageMapResult(
        BufferedImage image,
        RegionCoverageSummary summary,
        ReadDiagnostics mapRegionDiagnostics
) {
    public RenderCoverageMapResult {
        Objects.requireNonNull(image, "rendered image is required");
        Objects.requireNonNull(summary, "coverage summary is required");
        Objects.requireNonNull(mapRegionDiagnostics, "mapregion diagnostics are required");
    }
}
