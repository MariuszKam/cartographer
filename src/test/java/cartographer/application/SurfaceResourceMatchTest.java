package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
