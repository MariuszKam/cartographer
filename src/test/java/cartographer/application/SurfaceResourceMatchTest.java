package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.resource.SurfaceResourcePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceResourceMatchTest {

    @Test
    void fireClayRequiresBothTokens() {
        SurfaceResourceMatch match = new SurfaceResourceMatch(
                "Fire Clay",
                List.of("fire", "clay")
        );

        assertTrue(match.matches(block("game:fire-clay-blue")));
        assertFalse(match.matches(block("game:firepit")));
        assertFalse(match.matches(block("game:clay-blue")));
    }

    @Test
    void matchingIsCaseInsensitive() {
        SurfaceResourceMatch match = new SurfaceResourceMatch(
                "Fire Clay",
                List.of("FiRe", "ClAy")
        );

        assertTrue(match.matches(block("GAME:FIRE-CLAY-BLUE")));
    }

    @Test
    void customSubstringUsesOneToken() {
        SurfaceResourceMatch match = new SurfaceResourceMatch(
                "redclay",
                List.of("redclay")
        );

        assertTrue(match.matches(block("game:redclay")));
        assertFalse(match.matches(block("game:clay")));
    }

    @Test
    void looseObsidianMatchesApprovedFamiliesAndVariants() {
        SurfaceResourceMatch match = SurfaceResourceMatch.looseObsidian();

        assertTrue(match.matches(block("game:loosestones-obsidian-free")));
        assertTrue(match.matches(block("game:loosestones-obsidian-snow")));
        assertTrue(match.matches(block("game:looseflints-obsidian-free")));
        assertTrue(match.matches(block("somemod:looseboulders-obsidian-water")));
    }

    @Test
    void looseObsidianRejectsNonSurfaceAndUnrelatedCodes() {
        SurfaceResourceMatch match = SurfaceResourceMatch.looseObsidian();

        assertFalse(match.matches(block("game:rock-obsidian")));
        assertFalse(match.matches(block("game:ore-cassiterite-obsidian")));
        assertFalse(match.matches(block("game:looseores-obsidian-free")));
        assertFalse(match.matches(block("game:stone-obsidian")));
    }

    @Test
    void analyzerKeepsExactPointsAndGroupsAdjacentLooseObsidian() {
        SurfaceResourceMatch match = SurfaceResourceMatch.looseObsidian();
        SurfaceResourceAnalyzer analyzer = new SurfaceResourceAnalyzer();
        List<SurfaceBlock> blocks = List.of(
                new SurfaceBlock(10, 5, 20, new BlockInfo(1, "game:loosestones-obsidian-free")),
                new SurfaceBlock(11, 5, 20, new BlockInfo(2, "game:looseflints-obsidian-free"))
        );

        SurfaceResourceAnalysis result = analyzer.analyzeMatched(
                match.displayName(),
                match.matchingBlocks(blocks),
                2
        );

        assertEquals(2, result.matchingBlockCount());
        assertEquals(1, result.depositCount());
        assertEquals(
                List.of(10, 11),
                result.matchingBlocks().stream()
                        .map(SurfaceResourcePoint::worldX)
                        .toList()
        );
    }

    @Test
    void emptyMatchInputProducesValidAnalysis() {
        SurfaceResourceAnalysis analysis = new SurfaceResourceAnalyzer().analyze(
                List.of(),
                "peat"
        );

        assertTrue(analysis.matchingBlocks().isEmpty());
        assertTrue(analysis.deposits().isEmpty());
    }

    private SurfaceBlock block(String code) {
        return new SurfaceBlock(0, 100, 0, new BlockInfo(1, code));
    }
}
