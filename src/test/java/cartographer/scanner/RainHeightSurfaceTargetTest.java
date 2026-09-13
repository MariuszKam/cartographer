package cartographer.scanner;

import cartographer.model.ChunkPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RainHeightSurfaceTargetTest {

    @Test
    void coordinate31StaysInChunkZero() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(31, 31, 31);

        assertEquals(new ChunkPosition(0, 0, 0, 0), target.chunkPosition());
        assertEquals(31, target.localX());
        assertEquals(31, target.localY());
        assertEquals(31, target.localZ());
    }

    @Test
    void coordinate32StartsChunkOne() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(32, 32, 32);

        assertEquals(new ChunkPosition(1, 1, 1, 0), target.chunkPosition());
        assertEquals(0, target.localX());
        assertEquals(0, target.localY());
        assertEquals(0, target.localZ());
    }

    @Test
    void negativeCoordinateUsesFloorDivisionAndModulo() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(-1, -1, -1);

        assertEquals(new ChunkPosition(-1, -1, -1, 0), target.chunkPosition());
        assertEquals(31, target.localX());
        assertEquals(31, target.localY());
        assertEquals(31, target.localZ());
    }
}
