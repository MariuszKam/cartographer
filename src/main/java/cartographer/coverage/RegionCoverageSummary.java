package cartographer.coverage;

import java.util.List;

public record RegionCoverageSummary(
        boolean empty,
        List<RegionCoverageCell> cells,
        int minRegionX,
        int maxRegionX,
        int minRegionZ,
        int maxRegionZ,
        int gridWidth,
        int gridHeight,
        int possibleCells,
        int presentCells,
        int missingCells,
        double coveragePercentage,
        int worldMinX,
        int worldMinZ,
        int worldMaxXExclusive,
        int worldMaxZExclusive,
        double displayMinX,
        double displayMinZ,
        double displayMaxXExclusive,
        double displayMaxZExclusive
) {
    public RegionCoverageSummary {
        cells =
                cells == null
                        ? List.of()
                        : List.copyOf(
                        cells
                );
    }

    public static RegionCoverageSummary emptySummary() {
        return new RegionCoverageSummary(
                true,
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0,
                0.0
        );
    }
}