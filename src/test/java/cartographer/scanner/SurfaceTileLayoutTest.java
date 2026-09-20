package cartographer.scanner;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceTileLayoutTest {
    @Test
    void roundsSurfaceCenterLikeLegacyPath() {
        SurfaceTileLayout positive = SurfaceTileLayout.forSurface(
                1.5, 2.5, 1, new WorldMetadata(64, 256, 64));
        SurfaceTileLayout negative = SurfaceTileLayout.forSurface(
                -1.5, -2.5, 1, new WorldMetadata(64, 256, 64));

        assertEquals(2, positive.centerWorldX());
        assertEquals(3, positive.centerWorldZ());
        assertEquals(-1, negative.centerWorldX());
        assertEquals(-2, negative.centerWorldZ());
    }

    @Test
    void includesCircleBoundaryAndExcludesOutside() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                10, 10, 5, new WorldMetadata(64, 256, 64));

        assertTrue(layout.isActive(13, 14));
        assertFalse(layout.isActive(14, 14));
    }

    @Test
    void clipsWorldEdgesAndRetainsPartialEdgeTile() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                31, 31, 10, new WorldMetadata(35, 256, 35));

        assertTrue(layout.contains(34, 34));
        assertFalse(layout.contains(35, 34));
        assertEquals(3, layout.tileWidth(layout.lastTileX()));
        assertEquals(3, layout.tileHeight(layout.lastTileZ()));
        assertEquals(4, layout.tileCount());
    }

    @Test
    void negativeTileMappingRoundTripsWithFloorDivision() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                0, 0, 1, new WorldMetadata(64, 256, 64));

        assertEquals(-1, layout.tileXForWorld(-1));
        assertEquals(31, layout.localXForWorld(-1));
        assertEquals(-1, layout.tileZForWorld(-1));
        assertEquals(-1, layout.worldXForTileLocal(-1, 31));
        assertEquals(-1, layout.worldZForTileLocal(-1, 31));
    }

    @Test
    void tileAndCellOrderIsZThenX() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                32, 32, 32, new WorldMetadata(96, 256, 96));
        List<String> order = new ArrayList<>();
        for (int index = 0; index < layout.tileCount(); index++) {
            order.add(layout.tileXAt(index) + ":" + layout.tileZAt(index));
        }

        assertEquals(List.of("0:0", "1:0", "2:0", "0:1", "1:1", "2:1", "0:2", "1:2", "2:2"), order);
        assertEquals(1, layout.cellIndex(1, 1, 1, 0));
        assertEquals(MapChunk.SIZE + 1, layout.cellIndex(1, 1, 1, 1));
    }

    @Test
    void explicitMapChunkLayoutActivatesCompleteTilesAndLeavesHolesInactive() {
        SurfaceTileLayout layout = SurfaceTileLayout.forMapChunks(
                List.of(
                        new MapChunkCoordinate(0, 0),
                        new MapChunkCoordinate(2, 0)
                ),
                new WorldMetadata(96, 256, 64)
        );

        assertEquals(3, layout.tileWidthCount());
        assertEquals(1, layout.tileHeightCount());
        assertTrue(layout.isActive(0, 0));
        assertTrue(layout.isActive(31, 31));
        assertFalse(layout.isActive(32, 0));
        assertFalse(layout.isActive(63, 31));
        assertTrue(layout.isActive(64, 0));
        assertTrue(layout.isActive(95, 31));
    }

    @Test
    void explicitMapChunkLayoutRetainsPartialWorldEdgeTile() {
        SurfaceTileLayout layout = SurfaceTileLayout.forMapChunks(
                List.of(new MapChunkCoordinate(1, 1)),
                new WorldMetadata(35, 256, 35)
        );

        assertEquals(3, layout.tileWidth(1));
        assertEquals(3, layout.tileHeight(1));
        assertTrue(layout.isActive(34, 34));
        assertFalse(layout.contains(35, 34));
    }

    @Test
    void rejectsInvalidRadiusAndCheckedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> SurfaceTileLayout.forSurface(
                0, 0, 0, new WorldMetadata(64, 256, 64)));
        assertThrows(IllegalArgumentException.class, () -> SurfaceTileLayout.forSurface(
                0, 0, Integer.MAX_VALUE,
                new WorldMetadata(Integer.MAX_VALUE, 256, Integer.MAX_VALUE)));
    }
}
