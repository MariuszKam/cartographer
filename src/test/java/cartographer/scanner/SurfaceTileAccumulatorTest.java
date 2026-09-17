package cartographer.scanner;

import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceTileAccumulatorTest {
    private final SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
            16, 16, 2, new WorldMetadata(64, 256, 64));

    @Test
    void keepsExplicitUnresolvedAndConsideredState() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.consider(16, 16);
        accumulator.markLiquidUnavailable(17, 16);

        SurfaceMap map = accumulator.finish();

        assertTrue(map.isConsidered(16, 16));
        assertFalse(map.isResolved(16, 16));
        assertTrue(map.isConsidered(17, 16));
        assertTrue(map.isLiquidUnavailable(17, 16));
    }

    @Test
    void storesYZeroAndBlockZeroWithoutUsingThemAsSentinels() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 0, 0, 0, SurfaceClass.UNKNOWN);

        SurfaceMap map = accumulator.finish();

        assertTrue(map.isResolved(16, 16));
        assertEquals(0, map.surfaceYAt(16, 16));
        assertEquals(0, map.blockIdAt(16, 16));
        assertEquals(SurfaceClass.UNKNOWN, map.surfaceClassAt(16, 16));
    }

    @Test
    void higherYReplacesLowerButLowerDoesNotReplaceHigher() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 12, 2, 3, SurfaceClass.SOIL);
        accumulator.recordSurface(16, 16, 11, 9, 9, SurfaceClass.ROCK);
        accumulator.recordSurface(16, 16, 13, 4, 5, SurfaceClass.SAND);

        SurfaceMap map = accumulator.finish();

        assertEquals(13, map.surfaceYAt(16, 16));
        assertEquals(4, map.blockIdAt(16, 16));
        assertEquals(SurfaceClass.SAND, map.surfaceClassAt(16, 16));
    }

    @Test
    void equalHeightSelectionIsDeterministicAcrossUpdateOrder() {
        SurfaceTileAccumulator first = new SurfaceTileAccumulator(layout);
        SurfaceTileAccumulator second = new SurfaceTileAccumulator(layout);
        first.recordSurface(16, 16, 12, 2, 3, SurfaceClass.SOIL);
        first.recordSurface(16, 16, 12, 7, 1, SurfaceClass.ROCK);
        second.recordSurface(16, 16, 12, 7, 1, SurfaceClass.ROCK);
        second.recordSurface(16, 16, 12, 2, 3, SurfaceClass.SOIL);

        SurfaceMap firstMap = first.finish();
        SurfaceMap secondMap = second.finish();

        assertEquals(firstMap.blockIdAt(16, 16), secondMap.blockIdAt(16, 16));
        assertEquals(firstMap.liquidBlockIdAt(16, 16), secondMap.liquidBlockIdAt(16, 16));
        assertEquals(firstMap.surfaceClassAt(16, 16), secondMap.surfaceClassAt(16, 16));
    }

    @Test
    void preservesEverySurfaceClassThroughExplicitStableCodes() {
        SurfaceTileLayout classLayout = SurfaceTileLayout.forSurface(
                16, 16, 4, new WorldMetadata(64, 256, 64));
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(classLayout);
        SurfaceClass[] classes = SurfaceClass.values();
        int index = 0;
        for (int z = 12; z <= 20 && index < classes.length; z++) {
            for (int x = 12; x <= 20 && index < classes.length; x++) {
                if (classLayout.isActive(x, z)) {
                    accumulator.recordSurface(x, z, index, index, index + 1, classes[index]);
                    index++;
                }
            }
        }

        SurfaceMap map = accumulator.finish();
        index = 0;
        for (int z = 12; z <= 20 && index < classes.length; z++) {
            for (int x = 12; x <= 20 && index < classes.length; x++) {
                if (classLayout.isActive(x, z)) {
                    assertEquals(classes[index], map.surfaceClassAt(x, z));
                    index++;
                }
            }
        }
        assertEquals(classes.length, index);
    }

    @Test
    void finalizedMapOwnsStateAndRejectsFurtherMutation() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 0, 0, 0, SurfaceClass.UNKNOWN);
        SurfaceMap map = accumulator.finish();

        assertEquals(0, map.surfaceYAt(16, 16));
        assertThrows(IllegalStateException.class,
                () -> accumulator.recordSurface(16, 16, 99, 9, 9, SurfaceClass.ROCK));
        assertThrows(IllegalStateException.class, accumulator::finish);
    }

    @Test
    void fallbackResetPreservesActiveButClearsTransientFastStateAndPayload() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 42, 7, 8, SurfaceClass.ROCK);
        accumulator.markLiquidUnavailable(16, 16);

        accumulator.resetForFallbackTile(0, 0);

        accumulator.finish().forEachCell((x, z, state, y, blockId, liquidId, surfaceClass) -> {
            if (x == 16 && z == 16) {
                assertTrue((state & SurfaceTile.ACTIVE) != 0);
                assertFalse((state & SurfaceTile.CONSIDERED) != 0);
                assertFalse((state & SurfaceTile.RESOLVED) != 0);
                assertFalse((state & SurfaceTile.LIQUID_UNAVAILABLE) != 0);
                assertEquals(0, y);
                assertEquals(0, blockId);
                assertEquals(0, liquidId);
            }
        });
    }

    @Test
    void primitiveTraversalIsDeterministicAndDoesNotRequireSurfaceBlocks() {
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 42, 7, 8, SurfaceClass.WATER);
        SurfaceMap map = accumulator.finish();
        StringBuilder visited = new StringBuilder();

        map.forEachCell((x, z, state, y, blockId, liquidId, surfaceClass) -> {
            if ((state & SurfaceTile.RESOLVED) != 0) {
                visited.append(x).append(':').append(z).append(':').append(y)
                        .append(':').append(blockId).append(':').append(liquidId)
                        .append(':').append(surfaceClass).append(';');
            }
        });

        assertEquals("16:16:42:7:8:WATER;", visited.toString());
    }
}
