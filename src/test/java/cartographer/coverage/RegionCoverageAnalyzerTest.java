package cartographer.coverage;

import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCoverageAnalyzerTest {
    private final RegionCoverageAnalyzer analyzer =
            new RegionCoverageAnalyzer();

    private final WorldMetadata metadata =
            new WorldMetadata(
                    1024,
                    256,
                    1024
            );

    @Test
    void emptyInputProducesEmptySummary() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(),
                        metadata
                );

        assertTrue(
                summary.empty()
        );

        assertEquals(
                0,
                summary.presentCells()
        );
    }

    @Test
    void singleRegionHasCompleteOneCellCoverage() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(
                                region(
                                        1,
                                        2
                                )
                        ),
                        metadata
                );

        assertEquals(
                1,
                summary.gridWidth()
        );

        assertEquals(
                1,
                summary.gridHeight()
        );

        assertEquals(
                1,
                summary.presentCells()
        );

        assertEquals(
                0,
                summary.missingCells()
        );

        assertEquals(
                100.0,
                summary.coveragePercentage()
        );
    }

    @Test
    void completeRectangleHasNoMissingCells() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(
                                region(
                                        0,
                                        0
                                ),
                                region(
                                        1,
                                        0
                                ),
                                region(
                                        0,
                                        1
                                ),
                                region(
                                        1,
                                        1
                                )
                        ),
                        metadata
                );

        assertEquals(
                2,
                summary.gridWidth()
        );

        assertEquals(
                2,
                summary.gridHeight()
        );

        assertEquals(
                4,
                summary.possibleCells()
        );

        assertEquals(
                4,
                summary.presentCells()
        );

        assertEquals(
                0,
                summary.missingCells()
        );
    }

    @Test
    void sparseRectangleCountsHolesInsideBounds() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(
                                region(
                                        0,
                                        0
                                ),
                                region(
                                        2,
                                        1
                                )
                        ),
                        metadata
                );

        assertEquals(
                3,
                summary.gridWidth()
        );

        assertEquals(
                2,
                summary.gridHeight()
        );

        assertEquals(
                6,
                summary.possibleCells()
        );

        assertEquals(
                2,
                summary.presentCells()
        );

        assertEquals(
                4,
                summary.missingCells()
        );
    }

    @Test
    void duplicateCoordinatesAreDeduplicated() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(
                                region(
                                        3,
                                        4
                                ),
                                region(
                                        3,
                                        4
                                )
                        ),
                        metadata
                );

        assertEquals(
                1,
                summary.presentCells()
        );

        assertEquals(
                1,
                summary.possibleCells()
        );
    }

    @Test
    void negativeCoordinatesProduceStableBounds() {
        RegionCoverageSummary summary =
                analyzer.analyze(
                        List.of(
                                region(
                                        -2,
                                        -1
                                ),
                                region(
                                        -1,
                                        1
                                )
                        ),
                        metadata
                );

        assertEquals(
                -2,
                summary.minRegionX()
        );

        assertEquals(
                -1,
                summary.maxRegionX()
        );

        assertEquals(
                -1,
                summary.minRegionZ()
        );

        assertEquals(
                1,
                summary.maxRegionZ()
        );

        assertEquals(
                -1024,
                summary.worldMinX()
        );

        assertEquals(
                -512,
                summary.worldMinZ()
        );
    }

    private ServerMapRegion region(
            int x,
            int z
    ) {
        return new ServerMapRegion(
                new MapRegionCoordinate(
                        x,
                        z
                ),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of()
        );
    }
}
