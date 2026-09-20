package cartographer.render;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.scanner.SurfaceRegistryLookup;
import cartographer.scanner.SurfaceTileLayout;
import cartographer.soil.SoilFertilityTier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceRenderDataTest {

    @Test
    void laterSurfaceCellWinsSharedRasterPixels() {
        Fixture fixture = fixture();
        SurfaceRenderData.Builder builder = fixture.builder();

        builder.acceptResolved(20, 20, 1, SurfaceClass.GRASS);
        builder.acceptResolved(21, 20, 2, SurfaceClass.ROCK);
        SurfaceRenderData data = builder.finish();

        assertEquals(SurfaceClass.ROCK, data.surfaceClassAt(33, 32));
        assertEquals(21, data.surfaceWorldXAt(33, 32));
        assertEquals(20, data.surfaceWorldZAt(33, 32));
        assertTrue(data.surfaceClasses().contains(SurfaceClass.GRASS));
        assertTrue(data.surfaceClasses().contains(SurfaceClass.ROCK));
    }

    @Test
    void nonSoilSurfaceDoesNotEraseEarlierSoilOverlay() {
        Fixture fixture = fixture();
        SurfaceRenderData.Builder builder = fixture.builder();

        builder.acceptResolved(20, 20, 3, SurfaceClass.SOIL);
        builder.acceptResolved(21, 20, 2, SurfaceClass.ROCK);
        SurfaceRenderData data = builder.finish();

        assertEquals(SurfaceClass.ROCK, data.surfaceClassAt(33, 32));
        assertEquals(
                SoilFertilityTier.MEDIUM,
                data.soilTierAt(33, 32)
        );
        assertNull(data.soilTierAt(35, 32));
    }

    @Test
    void emptyDataDoesNotAllocateRasterSizedBackingArrays() {
        RenderSamplingPlan sampling = RenderSamplingPlan.from(
                new WorldPosition(0, 0, 0),
                new RenderOptions(
                        4096,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of()
                )
        );

        SurfaceRenderData data = SurfaceRenderData.empty(sampling);

        assertTrue(data.isEmpty());
        assertFalse(data.hasSurfaceAt(4095, 4095));
        assertNull(data.soilTierAt(4095, 4095));
        assertEquals(0, data.surfaceSourceByPixelView().length);
        assertEquals(0, data.surfaceClassByPixelView().length);
        assertEquals(0, data.soilTierByPixelView().length);
    }

    @Test
    void requestCircleRejectsResolvedCellsOutsideActiveDomain() {
        RenderOptions options = new RenderOptions(
                4,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE)
        );
        WorldPosition center = new WorldPosition(16, 0, 16);
        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                16,
                16,
                4,
                new WorldMetadata(64, 256, 64)
        );
        SurfaceRenderData.Builder builder = SurfaceRenderData.builder(
                sampling,
                layout,
                new SurfaceRegistryLookup(Map.of())
        );

        builder.acceptResolved(0, 0, 404, SurfaceClass.UNKNOWN);
        SurfaceRenderData data = builder.finish();

        assertFalse(data.hasSurfaceAt(0, 0));
    }

    private static Fixture fixture() {
        RenderOptions options = new RenderOptions(
                20,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE, RenderLayer.SOIL_FERTILITY)
        );
        WorldPosition center = new WorldPosition(20, 0, 20);
        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                20,
                20,
                20,
                new WorldMetadata(64, 256, 64)
        );
        SurfaceRegistryLookup registry = new SurfaceRegistryLookup(
                Map.of(
                        1, new BlockInfo(1, "game:grass"),
                        2, new BlockInfo(2, "game:rock-granite"),
                        3, new BlockInfo(3, "game:soil-medium-normal")
                )
        );
        return new Fixture(sampling, layout, registry);
    }

    private record Fixture(
            RenderSamplingPlan sampling,
            SurfaceTileLayout layout,
            SurfaceRegistryLookup registry
    ) {
        SurfaceRenderData.Builder builder() {
            return SurfaceRenderData.builder(
                    sampling,
                    layout,
                    registry
            );
        }
    }
}
