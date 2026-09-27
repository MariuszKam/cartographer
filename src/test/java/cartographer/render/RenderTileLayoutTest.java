package cartographer.render;

import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderTileLayoutTest {

    private final RenderTileLayout layout = new RenderTileLayout(4);

    @Nested
    class CoordinateMapping {

        @Test
        void mapsMapChunksAtPositiveTileBoundaries() {
            assertEquals(
                    new RenderTileCoordinate(0, 0),
                    layout.coordinateFor(new MapChunkCoordinate(3, 3))
            );
            assertEquals(
                    new RenderTileCoordinate(1, 1),
                    layout.coordinateFor(new MapChunkCoordinate(4, 4))
            );
        }

        @Test
        void mapsNegativeMapChunksUsingFloorDivision() {
            assertEquals(
                    new RenderTileCoordinate(-1, -1),
                    layout.coordinateFor(new MapChunkCoordinate(-1, -4))
            );
            assertEquals(
                    new RenderTileCoordinate(-2, -2),
                    layout.coordinateFor(new MapChunkCoordinate(-5, -8))
            );
        }

        @Test
        void mapsWorldCoordinatesAtMapChunkBoundaries() {
            RenderTileLayout oneMapChunkPerTile = new RenderTileLayout(1);

            assertEquals(
                    new RenderTileCoordinate(0, 0),
                    oneMapChunkPerTile.coordinateForWorld(31.999, 31.999)
            );
            assertEquals(
                    new RenderTileCoordinate(1, 1),
                    oneMapChunkPerTile.coordinateForWorld(32.0, 32.0)
            );
            assertEquals(
                    new RenderTileCoordinate(-1, -1),
                    oneMapChunkPerTile.coordinateForWorld(-32.0, -0.1)
            );
            assertEquals(
                    new RenderTileCoordinate(-2, -2),
                    oneMapChunkPerTile.coordinateForWorld(-32.1, -32.1)
            );
        }

        @Test
        void mapsWorldCoordinatesAtRenderTileBoundaries() {
            assertEquals(
                    new RenderTileCoordinate(0, 0),
                    layout.coordinateForWorld(127.999, 0.0)
            );
            assertEquals(
                    new RenderTileCoordinate(1, 1),
                    layout.coordinateForWorld(128.0, 128.0)
            );
            assertEquals(
                    new RenderTileCoordinate(-1, -1),
                    layout.coordinateForWorld(-0.1, -128.0)
            );
            assertEquals(
                    new RenderTileCoordinate(-2, -2),
                    layout.coordinateForWorld(-128.1, -128.1)
            );
        }
    }

    @Nested
    class BoundsAndMembership {

        @Test
        void derivesAlignedHalfOpenWorldBounds() {
            assertEquals(
                    new RenderTileBounds(128, -256, 256, -128),
                    layout.boundsFor(new RenderTileCoordinate(1, -2))
            );
            assertEquals(128, layout.worldBlocksPerSide());
        }

        @Test
        void enumeratesContainedMapChunksInDeterministicOrder() {
            RenderTileLayout twoByTwo = new RenderTileLayout(2);

            assertEquals(
                    List.of(
                            new MapChunkCoordinate(-2, 0),
                            new MapChunkCoordinate(-1, 0),
                            new MapChunkCoordinate(-2, 1),
                            new MapChunkCoordinate(-1, 1)
                    ),
                    twoByTwo.mapChunksFor(new RenderTileCoordinate(-1, 0))
            );
        }

        @Test
        void rejectsInvalidLayoutSpan() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RenderTileLayout(0)
            );
        }
    }
}
