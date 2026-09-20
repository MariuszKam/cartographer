package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.HomeState;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTerrainPreparationTest {

    @Test
    void streamsMapChunkHeightsIntoPreparedTerrain() {
        RenderOptions options = options(RenderLayer.TERRAIN);
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16), options, 1
        );
        builder.accept(chunk(0, 0, 55));
        MapTerrainPreparation terrain = builder.finish();

        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16, 0, 16),
                new WorldPosition(16, 0, 16),
                HomeState.absent(),
                terrain,
                List.of(),
                options
        );

        assertNotEquals(
                new TerrainPalette().background(RenderStyle.SIMPLE),
                rendered.image().getRGB(16, 16)
        );
    }

    @Test
    void terrainOnlyUsesRenderSizedHeightState() {
        RenderOptions options = options(RenderLayer.TERRAIN);
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16),
                options,
                1
        );
        builder.accept(chunk(0, 0, 55));
        MapTerrainPreparation terrain = builder.finish();

        assertTrue(terrain.heights() instanceof SampledTerrainHeightField);
        assertEquals(55, terrain.heights().minHeight());
        assertEquals(55, terrain.heights().maxHeight());
    }

    @Test
    void sourceStyleSurfacePreparationKeepsExactHeightState() {
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE)
        );
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16),
                options,
                1
        );
        builder.accept(chunk(0, 0, 55));
        MapTerrainPreparation terrain = builder.finish();

        assertTrue(terrain.heights() instanceof DenseHeightGrid);
        assertEquals(1024, terrain.heights().sampleCount());
    }

    @Test
    void snapshotSurfacePreparationUsesOnlyFinalSurfaceSourcesAndNeighbours() {
        WorldPosition center = new WorldPosition(16, 0, 16);
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE)
        );
        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);
        SurfaceRenderData.Builder surface =
                SurfaceRenderData.builder(
                        sampling,
                        cartographer.scanner.SurfaceTileLayout.forSurface(
                                center.x(),
                                center.z(),
                                16,
                                new cartographer.model.WorldMetadata(
                                        32,
                                        256,
                                        32
                                )
                        )
                );
        surface.acceptResolved(16, 16, cartographer.model.SurfaceClass.ROCK);

        MapTerrainPreparation.Builder builder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        1,
                        ProgressReporter.NONE,
                        surface.finish()
                );
        builder.accept(chunk(0, 0, 55));
        MapTerrainPreparation terrain = builder.finish();

        assertTrue(terrain.heights() instanceof SampledTerrainHeightField);
        assertTrue(terrain.heights().hasHeightAt(16, 16));
        assertTrue(terrain.heights().hasHeightAt(15, 16));
        assertTrue(terrain.heights().hasHeightAt(17, 16));
        assertTrue(terrain.heights().hasHeightAt(16, 15));
        assertTrue(terrain.heights().hasHeightAt(16, 17));
        assertEquals(5, terrain.heights().sampleCount());
    }

    @Test
    void countsAcceptedMapChunks() {
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16), options(RenderLayer.TERRAIN), 3
        );
        builder.accept(chunk(0, 0, 1));
        builder.accept(chunk(1, 0, 2));
        builder.accept(chunk(0, 1, 3));

        assertEquals(3, builder.finish().mapChunkCount());
    }

    @Test
    void surfaceOnlyHeightSemanticsStillUseMapChunkHeightAt() {
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16), options(RenderLayer.TERRAIN), 1
        );
        builder.accept(new MapChunk(
                new MapChunkCoordinate(0, 0), filled(45), filled(99)
        ));

        MapTerrainPreparation terrain = builder.finish();
        assertEquals(45, terrain.heights().heightAt(0, 0));
    }

    @Test
    void noTerrainOrSurfaceLayersAvoidHeightSamples() {
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16), options(RenderLayer.MARKERS), 1
        );
        builder.accept(chunk(0, 0, 1));
        MapTerrainPreparation terrain = builder.finish();

        assertEquals(1, terrain.mapChunkCount());
        assertEquals(0, terrain.heights().sampleCount());
    }

    @Test
    void duplicateAcceptedMapChunksPreserveLaterOverwriteSemantics() {
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                new WorldPosition(16, 0, 16), options(RenderLayer.TERRAIN), 2
        );
        builder.accept(chunk(0, 0, 10));
        builder.accept(chunk(0, 0, 20));
        MapTerrainPreparation terrain = builder.finish();

        assertEquals(20, terrain.heights().heightAt(0, 0));
        assertEquals(20, terrain.heights().minHeight());
        assertEquals(20, terrain.heights().maxHeight());
    }

    private static RenderOptions options(RenderLayer layer) {
        return new RenderOptions(16, 1, RenderStyle.SIMPLE, Set.of(layer));
    }

    private static MapChunk chunk(int x, int z, int height) {
        return new MapChunk(new MapChunkCoordinate(x, z), filled(height), new int[0]);
    }

    private static int[] filled(int value) {
        int[] values = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(values, value);
        return values;
    }
}
