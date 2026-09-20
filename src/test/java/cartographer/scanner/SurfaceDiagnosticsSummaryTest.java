package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceDiagnosticsSummaryTest {

    @Test
    void exactSurfaceSummaryPreservesReportingSemantics() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                16,
                16,
                4,
                new WorldMetadata(64, 256, 64)
        );
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(
                16, 16, 20, 7, 8, SurfaceClass.WATER
        );
        accumulator.recordSurface(
                17, 16, 21, 9, 0, SurfaceClass.UNKNOWN
        );
        accumulator.recordSurface(
                18, 16, 22, 404, 0, SurfaceClass.UNKNOWN
        );
        accumulator.recordSurface(
                19, 16, 23, 8, 0, SurfaceClass.UNKNOWN
        );

        SurfaceMapScanResult exact = new SurfaceMapScanResult(
                accumulator.finish(),
                Map.of(
                        7, new BlockInfo(7, "game:water"),
                        9, new BlockInfo(9, "unknown:404"),
                        404, new BlockInfo(404, "unknown:404"),
                        8, new BlockInfo(8, "mod:other-unknown")
                ),
                2,
                4,
                1,
                1
        );

        SurfaceDiagnosticsSummary summary =
                SurfaceDiagnosticsSummary.from(exact);

        assertEquals(2, summary.chunksScanned());
        assertEquals(4, summary.columnsScanned());
        assertEquals(1, summary.emptyColumns());
        assertEquals(1, summary.liquidUnavailableColumns());
        assertEquals(1, summary.waterColumns());
        assertEquals(3, summary.unknownSurfaceBlocks());
        assertEquals(
                "unknown:404",
                summary.topUnknownSurfaceBlockCodes(1).get(0).code()
        );
        assertEquals(
                2,
                summary.topUnknownSurfaceBlockCodes(1).get(0).count()
        );
    }
}
