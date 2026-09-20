package cartographer.render;

import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RockRenderSamplingPlanTest {

    @Test
    void preservesExistingCappedMidpointSampling() {
        RockRenderSamplingPlan plan = RockRenderSamplingPlan.from(
                new WorldPosition(0, 0, 0),
                4096
        );

        assertEquals(4096, plan.rasterSize());
        assertEquals(8193, plan.worldDiameter());
        assertEquals(-4096, plan.minWorldX());
        assertEquals(-4095, plan.worldXForImageX(0));
        assertEquals(4095, plan.worldXForImageX(4095));
        assertEquals(0, plan.imageXForWorldX(-4095));
        assertEquals(-1, plan.imageXForWorldX(-4096));
    }

    @Test
    void oneToOneSamplingMapsEveryWorldCell() {
        RockRenderSamplingPlan plan = RockRenderSamplingPlan.from(
                new WorldPosition(10, 0, 20),
                2
        );

        assertEquals(5, plan.rasterSize());
        for (int pixel = 0; pixel < 5; pixel++) {
            int worldX = 8 + pixel;
            assertEquals(worldX, plan.worldXForImageX(pixel));
            assertEquals(pixel, plan.imageXForWorldX(worldX));
        }
    }
}
