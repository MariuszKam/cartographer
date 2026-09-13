package cartographer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapChunkTest {

    @Test
    void rainHeightAtReturnsActualRainHeightValue() {
        int[] rain = new int[MapChunk.HEIGHT_VALUE_COUNT];
        int[] worldGen = new int[MapChunk.HEIGHT_VALUE_COUNT];
        rain[1 + 2 * MapChunk.SIZE] = 45;
        worldGen[1 + 2 * MapChunk.SIZE] = 99;

        MapChunk mapChunk = new MapChunk(
                new MapChunkCoordinate(0, 0),
                rain,
                worldGen
        );

        assertEquals(45, mapChunk.rainHeightAt(1, 2));
    }

    @Test
    void rainHeightAtRejectsMissingRainHeightMap() {
        MapChunk mapChunk = new MapChunk(
                new MapChunkCoordinate(0, 0),
                new int[0],
                new int[MapChunk.HEIGHT_VALUE_COUNT]
        );

        assertThrows(
                IllegalStateException.class,
                () -> mapChunk.rainHeightAt(0, 0)
        );
    }

    @Test
    void rainHeightAtValidatesLocalCoordinates() {
        MapChunk mapChunk = new MapChunk(
                new MapChunkCoordinate(0, 0),
                new int[MapChunk.HEIGHT_VALUE_COUNT],
                new int[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> mapChunk.rainHeightAt(-1, 0)
        );
    }
}
