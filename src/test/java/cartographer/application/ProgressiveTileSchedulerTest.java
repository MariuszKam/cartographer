package cartographer.application;

import cartographer.render.RenderLod;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveTileSchedulerTest {

    @Test
    void upgradesQueuedTilePriorityAndDeduplicatesIt() throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(4);
        RenderTileCoordinate tile = new RenderTileCoordinate(3, 4);

        assertTrue(scheduler.offer(
                tile,
                ProgressiveTilePriority.BACKGROUND
        ));
        assertTrue(scheduler.offer(
                tile,
                ProgressiveTilePriority.VIEWPORT
        ));
        assertFalse(scheduler.offer(
                tile,
                ProgressiveTilePriority.PREFETCH
        ));

        ProgressiveTileScheduler.ScheduledTile taken = scheduler.take();
        assertNotNull(taken);
        assertEquals(tile, taken.coordinate());
        assertEquals(ProgressiveTilePriority.VIEWPORT, taken.priority());
        assertEquals(0, scheduler.queuedCount());
        assertEquals(1, scheduler.inFlightCount());
    }

    @Test
    void higherPriorityRequestCanDisplaceWorstQueuedBackgroundTile()
            throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(2);
        RenderTileCoordinate first = new RenderTileCoordinate(0, 0);
        RenderTileCoordinate second = new RenderTileCoordinate(1, 0);
        RenderTileCoordinate viewport = new RenderTileCoordinate(9, 9);

        assertTrue(scheduler.offer(
                first,
                ProgressiveTilePriority.BACKGROUND
        ));
        assertTrue(scheduler.offer(
                second,
                ProgressiveTilePriority.BACKGROUND
        ));
        assertTrue(scheduler.offer(
                viewport,
                ProgressiveTilePriority.VIEWPORT
        ));

        ProgressiveTileScheduler.ScheduledTile viewportTaken =
                scheduler.take();
        ProgressiveTileScheduler.ScheduledTile firstTaken =
                scheduler.take();
        assertNotNull(viewportTaken);
        assertNotNull(firstTaken);
        assertEquals(viewport, viewportTaken.coordinate());
        assertEquals(first, firstTaken.coordinate());
    }
    @Test
    void sameCoordinateAtDifferentLevelsOfDetailAreDistinctWorkItems()
            throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(4);
        RenderTileCoordinate coordinate =
                new RenderTileCoordinate(2, 3);

        assertTrue(scheduler.offer(
                new RenderTileKey(coordinate, RenderLod.LOD_0),
                ProgressiveTilePriority.VIEWPORT
        ));
        assertTrue(scheduler.offer(
                new RenderTileKey(coordinate, RenderLod.LOD_3),
                ProgressiveTilePriority.VIEWPORT
        ));

        ProgressiveTileScheduler.ScheduledTile firstLod =
                scheduler.take();
        ProgressiveTileScheduler.ScheduledTile secondLod =
                scheduler.take();
        assertNotNull(firstLod);
        assertNotNull(secondLod);
        assertEquals(RenderLod.LOD_0, firstLod.key().lod());
        assertEquals(RenderLod.LOD_3, secondLod.key().lod());
    }


    @Test
    void completedTileCanBeRequestedAgainByViewportButNotBackground()
            throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(4);
        RenderTileCoordinate coordinate =
                new RenderTileCoordinate(6, 2);
        RenderTileKey key = RenderTileKey.fullDetail(coordinate);

        assertTrue(scheduler.offer(
                key,
                ProgressiveTilePriority.VIEWPORT
        ));
        assertEquals(key, takeKey(scheduler));
        scheduler.completed(key);

        assertFalse(scheduler.offer(
                key,
                ProgressiveTilePriority.BACKGROUND
        ));
        assertTrue(scheduler.offer(
                key,
                ProgressiveTilePriority.VIEWPORT
        ));
        assertEquals(key, takeKey(scheduler));
    }

    @Test
    void failedTileRemainsTerminalForInteractiveRequests() throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(4);
        RenderTileKey key = RenderTileKey.fullDetail(
                new RenderTileCoordinate(7, 3)
        );

        assertTrue(scheduler.offer(
                key,
                ProgressiveTilePriority.VIEWPORT
        ));
        assertEquals(key, takeKey(scheduler));
        scheduler.failed(key);

        assertFalse(scheduler.offer(
                key,
                ProgressiveTilePriority.VIEWPORT
        ));
    }

    @Test
    void staleInteractiveQueueEntriesAreDroppedForNewViewportDemand()
            throws Exception {
        ProgressiveTileScheduler scheduler =
                new ProgressiveTileScheduler(3);
        RenderTileKey staleViewport = RenderTileKey.fullDetail(
                new RenderTileCoordinate(0, 0)
        );
        RenderTileKey stalePrefetch = RenderTileKey.fullDetail(
                new RenderTileCoordinate(1, 0)
        );
        RenderTileKey background = RenderTileKey.fullDetail(
                new RenderTileCoordinate(2, 0)
        );
        RenderTileKey currentViewport = RenderTileKey.fullDetail(
                new RenderTileCoordinate(9, 9)
        );

        assertTrue(scheduler.offer(
                staleViewport,
                ProgressiveTilePriority.VIEWPORT
        ));
        assertTrue(scheduler.offer(
                stalePrefetch,
                ProgressiveTilePriority.PREFETCH
        ));
        assertTrue(scheduler.offer(
                background,
                ProgressiveTilePriority.BACKGROUND
        ));

        scheduler.retainInteractiveDemand(
                java.util.Set.of(currentViewport)
        );
        assertEquals(1, scheduler.queuedCount());
        assertTrue(scheduler.offer(
                currentViewport,
                ProgressiveTilePriority.VIEWPORT
        ));

        assertEquals(currentViewport, takeKey(scheduler));
        assertEquals(background, takeKey(scheduler));
    }


    private static RenderTileKey takeKey(
            ProgressiveTileScheduler scheduler
    ) throws InterruptedException {
        ProgressiveTileScheduler.ScheduledTile scheduled = scheduler.take();
        assertNotNull(scheduled);
        return scheduled.key();
    }

}
