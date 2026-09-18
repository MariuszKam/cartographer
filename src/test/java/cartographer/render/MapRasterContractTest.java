package cartographer.render;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRasterContractTest {
    private static final double DELTA = 1.0e-12;

    @Test
    void r2048UsesFull4kRasterAtAboutOneBlockPerPixel() {
        MapRasterContract contract = MapRasterContract.from(
                new RenderOptions(2048, 1, RenderStyle.SIMPLE, Set.of())
        );

        assertEquals(4096, contract.worldDiameterBlocks());
        assertEquals(4097L, contract.requestedRasterSize());
        assertEquals(4096, contract.rasterSize());
        assertEquals(1.0, contract.effectivePixelsPerBlock(), DELTA);
        assertEquals(1.0, contract.effectiveBlocksPerPixel(), DELTA);
        assertTrue(contract.capped());
    }

    @Test
    void r4096RepresentsTwoWorldBlocksPer4kPixel() {
        MapRasterContract contract = MapRasterContract.from(
                new RenderOptions(4096, 1, RenderStyle.SIMPLE, Set.of())
        );

        assertEquals(8192, contract.worldDiameterBlocks());
        assertEquals(8193L, contract.requestedRasterSize());
        assertEquals(4096, contract.rasterSize());
        assertEquals(0.5, contract.effectivePixelsPerBlock(), DELTA);
        assertEquals(2.0, contract.effectiveBlocksPerPixel(), DELTA);
        assertTrue(contract.capped());
    }

    @Test
    void normalRadiusRetainsRequestedResolutionWhenBelowCap() {
        MapRasterContract contract = MapRasterContract.from(
                new RenderOptions(512, 1, RenderStyle.SIMPLE, Set.of())
        );

        assertEquals(1024, contract.worldDiameterBlocks());
        assertEquals(1025L, contract.requestedRasterSize());
        assertEquals(1025, contract.rasterSize());
        assertFalse(contract.capped());
    }
}
