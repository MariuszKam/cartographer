package cartographer.parser;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.GeologyReport;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeologyAnalyzerTest {
    @Test
    void groupsKnownRockFamiliesAndMaterialsFromCompactSurface() {
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:rock-basalt"),
                3, new BlockInfo(3, "game:soil-medium")
        );
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(
                SurfaceTileLayout.forSurface(
                        0,
                        0,
                        4,
                        new WorldMetadata(16, 256, 16)
                )
        );
        accumulator.recordSurface(0, 0, 100, 1, 0, SurfaceClass.ROCK);
        accumulator.recordSurface(1, 0, 100, 2, 0, SurfaceClass.ROCK);
        accumulator.recordSurface(2, 0, 100, 3, 0, SurfaceClass.SOIL);
        SurfaceMapScanResult compact = new SurfaceMapScanResult(
                accumulator.finish(),
                registry,
                1,
                3,
                0,
                0
        );

        GeologyReport report = new GeologyAnalyzer().analyze(compact);

        assertEquals(3, report.samples());
        assertEquals(3, report.geologicalSamples());
        assertEquals(1, report.unknownSamples());
        assertEquals(1, report.rockFamilies().get("granite"));
        assertEquals(1, report.rockFamilies().get("basalt"));
        assertEquals(2, report.materialTypes().get("rock"));
        assertEquals(1, report.materialTypes().get("ground"));
    }

    @Test
    void compactAnalysisPreservesMissingZeroAirMaterialWithWaterLiquid() {
        Map<Integer, BlockInfo> registry = Map.of(
                7, new BlockInfo(7, "game:water-still")
        );
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(
                SurfaceTileLayout.forSurface(
                        0,
                        0,
                        2,
                        new WorldMetadata(8, 64, 8)
                )
        );
        accumulator.recordSurface(0, 0, 0, 0, 7, SurfaceClass.WATER);
        SurfaceMapScanResult compact = new SurfaceMapScanResult(
                accumulator.finish(),
                registry,
                1,
                1,
                0,
                0
        );

        GeologyReport report = new GeologyAnalyzer().analyze(compact);

        assertEquals(1, report.samples());
        assertEquals(0, report.geologicalSamples());
        assertEquals(1, report.unknownSamples());
        assertEquals(1, report.materialTypes().get("air"));
        assertEquals(1, compact.waterColumns());
    }
}
