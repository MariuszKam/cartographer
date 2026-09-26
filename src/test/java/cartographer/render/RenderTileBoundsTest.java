package cartographer.render;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderTileBoundsTest {

    @Test
    void clipsPartialWorldEdgeWithoutExpandingIt() {
        RenderTileBounds full = new RenderTileLayout(4)
                .boundsFor(new RenderTileCoordinate(0, 0));

        RenderTileBounds clipped = full.clipToWorld(
                new WorldMetadata(100, 256, 64)
        ).orElseThrow();

        assertEquals(new RenderTileBounds(0, 0, 100, 64), clipped);
        assertEquals(100, clipped.widthBlocks());
        assertEquals(64, clipped.heightBlocks());
        assertEquals(
                List.of(
                        new MapChunkCoordinate(0, 0),
                        new MapChunkCoordinate(1, 0),
                        new MapChunkCoordinate(2, 0),
                        new MapChunkCoordinate(3, 0),
                        new MapChunkCoordinate(0, 1),
                        new MapChunkCoordinate(1, 1),
                        new MapChunkCoordinate(2, 1),
                        new MapChunkCoordinate(3, 1)
                ),
                clipped.intersectingMapChunks()
        );
    }

    @Test
    void reportsTileOutsideWorldAsNoIntersection() {
        RenderTileBounds outside = new RenderTileLayout(4)
                .boundsFor(new RenderTileCoordinate(-1, 0));

        assertTrue(
                outside.clipToWorld(
                        new WorldMetadata(100, 256, 100)
                ).isEmpty()
        );
    }

    @Test
    void usesHalfOpenContainment() {
        RenderTileBounds bounds = new RenderTileBounds(-32, -64, 32, 0);

        assertTrue(bounds.containsWorldBlock(-32, -64));
        assertTrue(bounds.containsWorldBlock(31, -1));
        assertFalse(bounds.containsWorldBlock(32, -1));
        assertFalse(bounds.containsWorldBlock(31, 0));
    }

    @Test
    void rejectsEmptyBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderTileBounds(0, 0, 0, 32)
        );
    }
}
