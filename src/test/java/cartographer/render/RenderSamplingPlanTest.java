package cartographer.render;

import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSamplingPlanTest {
    private static final double DELTA = 1.0e-12;

    @Test
    void r4096SamplesExactlyTheWorldColumnsObservedByThe4kRaster() {
        RenderSamplingPlan plan = RenderSamplingPlan.from(
                new WorldPosition(0.0, 0.0, 0.0),
                options(4096)
        );

        assertEquals(4096, plan.rasterSize());
        assertEquals(8192, plan.worldDiameterBlocks());
        assertEquals(-4096, plan.worldMinX());
        assertEquals(-4096, plan.worldMinZ());
        assertEquals(2.0, plan.effectiveBlocksPerPixel(), DELTA);
        assertTrue(plan.capped());

        assertEquals(-4096, plan.worldXForImageColumn(0));
        assertEquals(-4094, plan.worldXForImageColumn(1));
        assertEquals(4094, plan.worldXForImageColumn(4095));
        assertEquals(-4096, plan.worldZForImageRow(0));
        assertEquals(4094, plan.worldZForImageRow(4095));
    }

    @Test
    void r2048RetainsOneWorldColumnPerRasterPixelAtTheCap() {
        RenderSamplingPlan plan = RenderSamplingPlan.from(
                new WorldPosition(0.0, 0.0, 0.0),
                options(2048)
        );

        assertEquals(4096, plan.rasterSize());
        assertEquals(1.0, plan.effectiveBlocksPerPixel(), DELTA);
        assertEquals(-2048, plan.worldXForImageColumn(0));
        assertEquals(-2047, plan.worldXForImageColumn(1));
        assertEquals(2047, plan.worldXForImageColumn(4095));
    }

    @Test
    void preservesExistingFractionalCenterFlooringAndViewportGeometry() {
        RenderSamplingPlan plan = RenderSamplingPlan.from(
                new WorldPosition(100.75, 0.0, 100.25),
                new RenderOptions(1, 1, RenderStyle.SIMPLE, Set.of())
        );

        assertEquals(99, plan.worldMinX());
        assertEquals(99, plan.worldMinZ());
        assertEquals(99.0, plan.geometry().worldMinX(), DELTA);
        assertEquals(99.0, plan.geometry().worldMinZ(), DELTA);
        assertEquals(101.0, plan.geometry().worldMaxXExclusive(), DELTA);
        assertEquals(101.0, plan.geometry().worldMaxZExclusive(), DELTA);
        assertEquals(99, plan.worldXForImageColumn(0));
        assertEquals(100, plan.worldXForImageColumn(plan.rasterSize() - 1));
    }

    @Test
    void sampledCoordinatesRejectPixelsOutsideTheRaster() {
        RenderSamplingPlan plan = RenderSamplingPlan.from(
                new WorldPosition(0.0, 0.0, 0.0),
                options(16)
        );

        assertThrows(IndexOutOfBoundsException.class,
                () -> plan.worldXForImageColumn(-1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> plan.worldXForImageColumn(plan.rasterSize()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> plan.worldZForImageRow(plan.rasterSize()));
    }

    private static RenderOptions options(int radius) {
        return new RenderOptions(
                radius,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN)
        );
    }
}
