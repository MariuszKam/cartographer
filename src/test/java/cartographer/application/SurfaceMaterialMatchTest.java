package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.resource.SurfaceResourcePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceMaterialMatchTest {

    @Test
    void fireClayRequiresBothTokens() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("fire", "clay")
        );

        assertTrue(match.matches(block("game:fire-clay-blue")));
        assertFalse(match.matches(block("game:firepit")));
        assertFalse(match.matches(block("game:clay-blue")));
    }

    @Test
    void matchingIsCaseInsensitive() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("FiRe", "ClAy")
        );

        assertTrue(match.matches(block("GAME:FIRE-CLAY-BLUE")));
    }

    @Test
    void customSubstringUsesOneToken() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "redclay", List.of("redclay")
        );

        assertTrue(match.matches(block("game:redclay")));
        assertFalse(match.matches(block("game:clay")));
    }

    @Test
    void clayAndPeatMatchTheirMaterialTokens() {
        assertTrue(new SurfaceMaterialMatch("Clay", List.of("clay"))
                .matches(block("game:clay-blue")));
        assertTrue(new SurfaceMaterialMatch("Peat", List.of("peat"))
                .matches(block("game:peat-normal")));
        assertFalse(new SurfaceMaterialMatch("Peat", List.of("peat"))
                .matches(block("game:clay-blue")));
    }

    @Test
    void analyzerKeepsExactPointsAndGroupsAdjacentMaterialBlocks() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("fire", "clay")
        );
        SurfaceResourceAnalyzer analyzer = new SurfaceResourceAnalyzer();
        List<SurfaceBlock> blocks = List.of(
                new SurfaceBlock(10, 5, 20, new BlockInfo(1, "game:fire-clay-blue")),
                new SurfaceBlock(11, 5, 20, new BlockInfo(2, "game:fire-clay-blue"))
        );

        SurfaceResourceAnalysis result = analyzer.analyzeMatched(
                match.displayName(), match.matchingBlocks(blocks), 2
        );

        assertEquals(2, result.matchingBlockCount());
        assertEquals(1, result.depositCount());
        assertEquals(List.of(10, 11), result.matchingBlocks().stream()
                .map(SurfaceResourcePoint::worldX).toList());
    }

    @Test
    void emptyMatchInputProducesValidAnalysis() {
        SurfaceResourceAnalysis analysis = new SurfaceResourceAnalyzer().analyze(
                List.of(), "peat"
        );

        assertTrue(analysis.matchingBlocks().isEmpty());
        assertTrue(analysis.deposits().isEmpty());
    }

    private SurfaceBlock block(String code) {
        return new SurfaceBlock(0, 100, 0, new BlockInfo(1, code));
    }
}
