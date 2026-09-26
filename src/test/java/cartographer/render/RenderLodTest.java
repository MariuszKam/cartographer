package cartographer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderLodTest {

    @Test
    void selectsDeterministicPowerOfTwoLodForViewportScale() {
        assertEquals(RenderLod.LOD_0, RenderLod.forPixelsPerBlock(1.0));
        assertEquals(RenderLod.LOD_1, RenderLod.forPixelsPerBlock(0.5));
        assertEquals(RenderLod.LOD_2, RenderLod.forPixelsPerBlock(0.25));
        assertEquals(RenderLod.LOD_3, RenderLod.forPixelsPerBlock(0.125));
        assertEquals(RenderLod.LOD_5, RenderLod.forPixelsPerBlock(0.03125));
    }

    @Test
    void resolvesOnlySupportedBlocksPerPixelValues() {
        assertEquals(RenderLod.LOD_4, RenderLod.forBlocksPerPixel(16));
        assertThrows(
                IllegalArgumentException.class,
                () -> RenderLod.forBlocksPerPixel(3)
        );
    }
}
