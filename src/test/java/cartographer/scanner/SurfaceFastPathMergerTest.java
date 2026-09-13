package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceFastPathMergerTest {

    private static final WorldMetadata WORLD = new WorldMetadata(64, 256, 64);
    private final SurfaceFastPathMerger merger = new SurfaceFastPathMerger();

    @Test
    void keepsFastBlocksFromHealthyMapChunk() {
        SurfaceBlock block = block(1, 45, 1, 7);

        assertEquals(List.of(block), merge(List.of(block), List.of(), List.of()));
    }

    @Test
    void fallbackCompletelyReplacesFastBlocksInFallbackMapChunk() {
        SurfaceBlock fast = block(1, 45, 1, 7);
        SurfaceBlock fallback = block(2, 40, 2, 8);

        assertEquals(
                List.of(fallback),
                merge(
                        List.of(fast),
                        List.of(fallback),
                        List.of(new MapChunkCoordinate(0, 0))
                )
        );
    }

    @Test
    void fallbackWinsSameWorldColumn() {
        SurfaceBlock fast = block(1, 100, 1, 7);
        SurfaceBlock fallback = block(1, 50, 1, 8);

        assertEquals(List.of(fallback), merge(
                List.of(fast), List.of(fallback), List.of(new MapChunkCoordinate(0, 0))
        ));
    }

    @Test
    void higherYWinsDuplicateWithinFastSource() {
        SurfaceBlock lower = block(1, 40, 1, 7);
        SurfaceBlock higher = block(1, 50, 1, 8);

        assertEquals(List.of(higher), merge(List.of(lower, higher), List.of(), List.of()));
    }

    @Test
    void higherYWinsDuplicateWithinFallbackSource() {
        SurfaceBlock lower = block(1, 40, 1, 7);
        SurfaceBlock higher = block(1, 50, 1, 8);

        assertEquals(List.of(higher), merge(
                List.of(), List.of(lower, higher), List.of(new MapChunkCoordinate(0, 0))
        ));
    }

    @Test
    void filtersFallbackOutsideExactCircle() {
        SurfaceBlock outside = block(40, 0, 0, 7);

        assertTrue(merge(List.of(), List.of(outside), List.of()).isEmpty());
    }

    @Test
    void filtersFallbackOutsideWorldBounds() {
        SurfaceBlock outside = block(64, 0, 1, 7);

        assertTrue(merge(List.of(), List.of(outside), List.of()).isEmpty());
    }

    @Test
    void filtersFastOutsideExactCircleDefensively() {
        SurfaceBlock outside = block(40, 0, 0, 7);

        assertTrue(merge(List.of(outside), List.of(), List.of()).isEmpty());
    }

    @Test
    void resultContainsAtMostOneBlockPerWorldXZ() {
        SurfaceBlock first = block(1, 40, 1, 7);
        SurfaceBlock second = block(1, 50, 1, 8);

        assertEquals(1, merge(List.of(first, second), List.of(), List.of()).size());
    }

    @Test
    void outputOrderingIsDeterministic() {
        SurfaceBlock z2 = block(2, 45, 2, 7);
        SurfaceBlock z1x2 = block(2, 45, 1, 8);
        SurfaceBlock z1x1 = block(1, 45, 1, 9);

        assertEquals(
                List.of(z1x1, z1x2, z2),
                merge(List.of(z2, z1x2, z1x1), List.of(), List.of())
        );
    }

    @Test
    void fastHasTwoBlocksInFallbackMapChunkButFallbackHasOne() {
        SurfaceBlock fastFirst = block(1, 45, 1, 7);
        SurfaceBlock fastSecond = block(2, 45, 2, 7);
        SurfaceBlock fallback = block(1, 40, 1, 8);

        assertEquals(
                List.of(fallback),
                merge(
                        List.of(fastFirst, fastSecond),
                        List.of(fallback),
                        List.of(new MapChunkCoordinate(0, 0))
                )
        );
    }

    private List<SurfaceBlock> merge(
            List<SurfaceBlock> fast,
            List<SurfaceBlock> fallback,
            List<MapChunkCoordinate> fallbackMapChunks
    ) {
        return merger.merge(
                fast,
                fallback,
                fallbackMapChunks,
                WORLD,
                0,
                0,
                32
        );
    }

    private SurfaceBlock block(int x, int y, int z, int id) {
        return new SurfaceBlock(
                x,
                y,
                z,
                new BlockInfo(id, "rock-test-" + id)
        );
    }
}
