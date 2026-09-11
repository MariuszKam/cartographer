package cartographer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateTest {
    @Test
    void convertsPositiveWorldCoordinatesToChunkCoordinates() {
        ChunkCoordinate chunk = ChunkCoordinate.fromWorld(63.9, 64.0);

        assertEquals(new ChunkCoordinate(1, 2), chunk);
    }

    @Test
    void convertsNegativeWorldCoordinatesUsingFloorDivision() {
        ChunkCoordinate chunk = ChunkCoordinate.fromWorld(-0.1, -32.1);

        assertEquals(new ChunkCoordinate(-1, -2), chunk);
    }

    @Test
    void convertsMapChunksToRegionsUsingFloorDivision() {
        assertEquals(new RegionCoordinate(0, 1), RegionCoordinate.fromMapChunk(15, 16));
        assertEquals(new RegionCoordinate(-1, -2), RegionCoordinate.fromMapChunk(-1, -17));
    }
}
