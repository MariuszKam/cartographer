package cartographer.render;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenseHeightGridTest {

    @Test
    void storesAndRetrievesHeightByWorldCoordinate() {
        DenseHeightGrid grid = grid(
                List.of(chunk(0, 0, 45)),
                0,
                0,
                32,
                32
        );

        assertTrue(grid.hasHeightAt(3, 4));
        assertEquals(45, grid.heightAt(3, 4));
    }

    @Test
    void missingCoordinateIsNotPresent() {
        DenseHeightGrid grid = grid(
                List.of(chunk(0, 0, 45)), 0, 0, 2, 2
        );

        assertFalse(grid.hasHeightAt(2, 2));
        assertThrows(IllegalArgumentException.class, () -> grid.heightAt(2, 2));
    }

    @Test
    void negativeWorldCoordinatesIndexCorrectly() {
        DenseHeightGrid grid = grid(
                List.of(chunk(-1, -1, 10)), -32, -32, 32, 32
        );

        assertEquals(10, grid.heightAt(-32, -32));
        assertEquals(10, grid.heightAt(-22, -22));
    }

    @Test
    void clipsMapChunkCellsOutsideGrid() {
        DenseHeightGrid grid = grid(
                List.of(chunk(0, 0, 1)), 10, 10, 2, 2
        );

        assertEquals(4, grid.sampleCount());
    }

    @Test
    void usesMapChunkHeightAtSemantics() {
        int[] rain = filled(45);
        int[] worldGen = filled(99);
        DenseHeightGrid grid = grid(
                List.of(new MapChunk(new MapChunkCoordinate(0, 0), rain, worldGen)),
                0, 0, 1, 1
        );

        assertEquals(45, grid.heightAt(0, 0));
    }

    @Test
    void fallsBackToWorldGenThroughMapChunkHeightAt() {
        DenseHeightGrid grid = grid(
                List.of(new MapChunk(new MapChunkCoordinate(0, 0), new int[0], filled(99))),
                0, 0, 1, 1
        );

        assertEquals(99, grid.heightAt(0, 0));
    }

    @Test
    void duplicateOverwriteDoesNotPolluteMinMax() {
        DenseHeightGrid grid = grid(
                List.of(chunk(0, 0, 1), chunk(0, 0, 100)),
                0, 0, 1, 1
        );

        assertEquals(100, grid.heightAt(0, 0));
        assertEquals(100, grid.minHeight());
        assertEquals(100, grid.maxHeight());
    }

    @Test
    void emptyGridUsesZeroMinMax() {
        DenseHeightGrid grid = DenseHeightGrid.empty();

        assertEquals(0, grid.sampleCount());
        assertEquals(0, grid.minHeight());
        assertEquals(0, grid.maxHeight());
    }

    @Test
    void boundaryCellsAreIncludedAndOutsideCellsAreAbsent() {
        DenseHeightGrid grid = grid(
                List.of(chunk(0, 0, 1)), 0, 0, 2, 2
        );

        assertTrue(grid.hasHeightAt(0, 0));
        assertTrue(grid.hasHeightAt(1, 1));
        assertFalse(grid.hasHeightAt(-1, 0));
        assertFalse(grid.hasHeightAt(-2, -1));
        assertFalse(grid.hasHeightAt(0, 2));
    }

    private static DenseHeightGrid grid(
            List<MapChunk> chunks,
            int minWorldX,
            int minWorldZ,
            int width,
            int height
    ) {
        DenseHeightGrid.Builder builder = DenseHeightGrid.builder(
                minWorldX,
                minWorldZ,
                width,
                height,
                cartographer.application.ProgressReporter.NONE,
                chunks.size()
        );
        chunks.forEach(builder::accept);
        return builder.finish();
    }

    private static MapChunk chunk(int x, int z, int baseHeight) {
        return new MapChunk(
                new MapChunkCoordinate(x, z),
                filled(baseHeight),
                new int[0]
        );
    }

    private static int[] filled(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, value);
        return heights;
    }
}
