package cartographer.parser;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.GeologyReport;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
