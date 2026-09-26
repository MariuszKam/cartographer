package cartographer.render;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampledTerrainHeightFieldTest {

    @Test
    void retainsRasterSamplesAndImmediateHillshadeNeighbours() {
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN)
        );
        RenderSamplingPlan sampling = RenderSamplingPlan.from(
                new WorldPosition(16, 0, 16),
                options
        );
        SampledTerrainHeightField.Builder builder =
                SampledTerrainHeightField.builder(
                        sampling,
                        1
                );
        builder.accept(chunk());
        SampledTerrainHeightField field = builder.finish();

        int worldX = sampling.worldXForImageColumn(32);
        int worldZ = sampling.worldZForImageRow(32);
        assertTrue(field.hasHeightAt(worldX, worldZ));
        assertTrue(field.hasHeightAt(worldX - 1, worldZ));
        assertTrue(field.hasHeightAt(worldX + 1, worldZ));
        assertTrue(field.hasHeightAt(worldX, worldZ - 1));
        assertTrue(field.hasHeightAt(worldX, worldZ + 1));
        assertEquals(10 + worldX + worldZ, field.heightAt(worldX, worldZ));
    }

    @Test
    void retainsArbitrarySurfaceSourcesAndHillshadeNeighbours() {
        WorldPosition center = new WorldPosition(16, 0, 16);
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE)
        );
        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);
        SurfaceRenderData.Builder surface = SurfaceRenderData.builder(
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
        surface.acceptResolved(
                11,
                13,
                cartographer.model.SurfaceClass.ROCK
        );

        SampledTerrainHeightField.Builder builder =
                SampledTerrainHeightField.builder(
                        sampling,
                        false,
                        surface.finish(),
                        cartographer.progress.ProgressReporter.NONE,
                        1
                );
        builder.accept(chunk());
        SampledTerrainHeightField field = builder.finish();

        assertTrue(field.hasHeightAt(11, 13));
        assertTrue(field.hasHeightAt(10, 13));
        assertTrue(field.hasHeightAt(12, 13));
        assertTrue(field.hasHeightAt(11, 12));
        assertTrue(field.hasHeightAt(11, 14));
        assertFalse(field.hasHeightAt(9, 13));
    }

    @Test
    void preservesFullViewportMinMaxAndLaterDuplicateOverwrite() {
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN)
        );
        RenderSamplingPlan sampling = RenderSamplingPlan.from(
                new WorldPosition(16, 0, 16),
                options
        );
        SampledTerrainHeightField.Builder builder =
                SampledTerrainHeightField.builder(
                        sampling,
                        2
                );
        builder.accept(flatChunk(0, 0, 1));
        builder.accept(flatChunk(0, 0, 100));
        SampledTerrainHeightField field = builder.finish();

        assertEquals(100, field.minHeight());
        assertEquals(100, field.maxHeight());
        assertEquals(100, field.heightAt(0, 0));
    }

    @Test
    void outsideViewportOrUnretainedCoordinateIsAbsent() {
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN)
        );
        RenderSamplingPlan sampling = RenderSamplingPlan.from(
                new WorldPosition(16, 0, 16),
                options
        );
        SampledTerrainHeightField.Builder builder =
                SampledTerrainHeightField.builder(
                        sampling,
                        1
                );
        builder.accept(flatChunk(0, 0, 10));
        SampledTerrainHeightField field = builder.finish();

        assertFalse(field.hasHeightAt(-1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> field.heightAt(-1, 0)
        );
    }

    private static MapChunk chunk() {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
            for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                heights[localZ * MapChunk.SIZE + localX] =
                        10 + localX + localZ;
            }
        }
        return new MapChunk(
                new MapChunkCoordinate(0, 0),
                heights,
                new int[0]
        );
    }

    private static MapChunk flatChunk(int height) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, height);
        return new MapChunk(
                new MapChunkCoordinate(0, 0),
                heights,
                new int[0]
        );
    }
}
