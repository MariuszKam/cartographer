package cartographer.ui.workstation;

import cartographer.render.RenderLod;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapViewportTileCacheTest {

    @Test
    void demandRequestsOnlyMissingTilesAndCoalescesPendingRequests() {
        WorldMapViewportTileCache<String> cache =
                new WorldMapViewportTileCache<>(4);
        RenderTileCoordinate visible = new RenderTileCoordinate(0, 0);
        RenderTileCoordinate prefetch = new RenderTileCoordinate(1, 0);
        cache.updateDemand(new WorldMapViewportDemand(
                List.of(visible),
                List.of(prefetch),
                RenderLod.LOD_0
        ));

        WorldMapViewportDemand first = cache.missingDemand();
        WorldMapViewportDemand second = cache.missingDemand();

        assertEquals(List.of(visible), first.visible());
        assertEquals(List.of(prefetch), first.prefetch());
        assertTrue(second.visible().isEmpty());
        assertTrue(second.prefetch().isEmpty());
    }

    @Test
    void tilesOutsideCurrentDemandAreNotAdmitted() {
        WorldMapViewportTileCache<String> cache =
                new WorldMapViewportTileCache<>(2);
        RenderTileCoordinate visible = new RenderTileCoordinate(0, 0);
        RenderTileCoordinate background = new RenderTileCoordinate(9, 9);
        cache.updateDemand(new WorldMapViewportDemand(
                List.of(visible),
                List.of(),
                RenderLod.LOD_0
        ));

        assertFalse(cache.accept(
                new RenderTileKey(background, RenderLod.LOD_0),
                "background"
        ));
        assertTrue(cache.accept(
                new RenderTileKey(visible, RenderLod.LOD_0),
                "visible"
        ));

        assertEquals(1, cache.size());
        assertEquals(
                "visible",
                cache.best(new RenderTileKey(visible, RenderLod.LOD_0))
        );
    }

    @Test
    void currentVisibleTilesOutliveStaleTilesWhenCapacityIsReached() {
        WorldMapViewportTileCache<String> cache =
                new WorldMapViewportTileCache<>(2);
        RenderTileCoordinate first = new RenderTileCoordinate(0, 0);
        RenderTileCoordinate second = new RenderTileCoordinate(1, 0);
        RenderTileCoordinate next = new RenderTileCoordinate(2, 0);

        cache.updateDemand(new WorldMapViewportDemand(
                List.of(first, second),
                List.of(),
                RenderLod.LOD_0
        ));
        assertTrue(cache.accept(
                new RenderTileKey(first, RenderLod.LOD_0),
                "first"
        ));
        assertTrue(cache.accept(
                new RenderTileKey(second, RenderLod.LOD_0),
                "second"
        ));

        cache.updateDemand(new WorldMapViewportDemand(
                List.of(second, next),
                List.of(),
                RenderLod.LOD_0
        ));
        assertTrue(cache.accept(
                new RenderTileKey(next, RenderLod.LOD_0),
                "next"
        ));

        assertNull(cache.best(new RenderTileKey(first, RenderLod.LOD_0)));
        assertEquals(
                "second",
                cache.best(new RenderTileKey(second, RenderLod.LOD_0))
        );
        assertEquals(
                "next",
                cache.best(new RenderTileKey(next, RenderLod.LOD_0))
        );
    }

    @Test
    void exactCurrentLodReplacesFallbackAndRejectsLateOlderRaster() {
        WorldMapViewportTileCache<String> cache =
                new WorldMapViewportTileCache<>(4);
        RenderTileCoordinate coordinate = new RenderTileCoordinate(4, 7);
        cache.updateDemand(new WorldMapViewportDemand(
                List.of(coordinate),
                List.of(),
                RenderLod.LOD_3
        ));

        assertTrue(cache.accept(
                new RenderTileKey(coordinate, RenderLod.LOD_1),
                "fallback"
        ));
        assertTrue(cache.accept(
                new RenderTileKey(coordinate, RenderLod.LOD_3),
                "exact"
        ));
        assertFalse(cache.accept(
                new RenderTileKey(coordinate, RenderLod.LOD_0),
                "late-old"
        ));

        assertEquals(
                "exact",
                cache.best(new RenderTileKey(coordinate, RenderLod.LOD_3))
        );
        assertEquals(1, cache.size());
    }
}
