package cartographer.parser;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.GeologyReport;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeologyAnalyzerTest {
    @Test
    void groupsKnownRockFamiliesAndMaterials() {
        GeologyReport report = new GeologyAnalyzer().analyze(List.of(
                new SurfaceBlock(0, 100, 0, new BlockInfo(1, "game:rock-granite")),
                new SurfaceBlock(1, 100, 0, new BlockInfo(2, "game:rock-basalt")),
                new SurfaceBlock(2, 100, 0, new BlockInfo(3, "game:soil-medium"))));

        assertEquals(3, report.samples());
        assertEquals(3, report.geologicalSamples());
        assertEquals(1, report.rockFamilies().get("granite"));
        assertEquals(1, report.rockFamilies().get("basalt"));
        assertEquals(2, report.materialTypes().get("rock"));
        assertEquals(1, report.materialTypes().get("ground"));
    }

    @Test
    void compactAnalysisMatchesLegacyReport() {
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:rock-basalt"),
                3, new BlockInfo(3, "game:soil-medium")
        );
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(
                SurfaceTileLayout.forSurface(0, 0, 4, new WorldMetadata(16, 256, 16)));
        accumulator.recordSurface(0, 0, 100, 1, 0, SurfaceClass.ROCK);
        accumulator.recordSurface(1, 0, 100, 2, 0, SurfaceClass.ROCK);
        accumulator.recordSurface(2, 0, 100, 3, 0, SurfaceClass.SOIL);
        SurfaceMapScanResult compact = new SurfaceMapScanResult(
                accumulator.finish(), registry, 1, 3, 0, 0);

        List<SurfaceBlock> legacyBlocks = List.of(
                new SurfaceBlock(0, 100, 0, registry.get(1)),
                new SurfaceBlock(1, 100, 0, registry.get(2)),
                new SurfaceBlock(2, 100, 0, registry.get(3)));

        assertEquals(
                new GeologyAnalyzer().analyze(legacyBlocks),
                new GeologyAnalyzer().analyze(compact));
    }
}
