package cartographer.ui.workstation;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLod;
import cartographer.render.RenderTileBounds;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapViewportModelTest {

    @Test
    void worldAndViewportTransformsRoundTrip() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(800, 600);
        model.centerOn(100.0, -50.0);

        WorldMapViewportModel.ViewportPoint viewport =
                model.viewportAt(132.0, -18.0);
        MapCursorPosition world = model.worldAt(
                viewport.x(),
                viewport.y()
        );

        assertEquals(132.0, world.absoluteX(), 1.0e-9);
        assertEquals(-18.0, world.absoluteZ(), 1.0e-9);
    }

    @Test
    void zoomKeepsAnchorWorldPointStable() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(800, 600);
        model.centerOn(0.0, 0.0);
        MapCursorPosition before = model.worldAt(700, 200);

        model.zoomAt(2.0, 700, 200);

        MapCursorPosition after = model.worldAt(700, 200);
        assertEquals(before.absoluteX(), after.absoluteX(), 1.0e-9);
        assertEquals(before.absoluteZ(), after.absoluteZ(), 1.0e-9);
    }

    @Test
    void visibleDemandHandlesNegativeWorldCoordinates() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(1));
        model.setViewportSize(64, 64);
        model.centerOn(new WorldPosition(0.0, 0.0, 0.0));

        WorldMapViewportDemand demand = model.demand(0);

        assertEquals(
                List.of(
                        new RenderTileCoordinate(-1, -1),
                        new RenderTileCoordinate(0, -1),
                        new RenderTileCoordinate(-1, 0),
                        new RenderTileCoordinate(0, 0)
                ),
                demand.visible()
        );
    }

    @Test
    void prefetchIsDistinctFromVisibleTiles() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(1));
        model.setViewportSize(32, 32);
        model.centerOn(16.0, 16.0);

        WorldMapViewportDemand demand = model.demand(1);

        assertEquals(
                List.of(new RenderTileCoordinate(0, 0)),
                demand.visible()
        );
        assertEquals(8, demand.prefetch().size());
        assertFalse(demand.prefetch().contains(
                new RenderTileCoordinate(0, 0)
        ));
    }

    @Test
    void fitCentersAndScalesKnownWorldBounds() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(1000, 500);

        model.fit(
                new RenderTileBounds(100, 200, 500, 400),
                50
        );

        assertEquals(300.0, model.centerWorldX(), 1.0e-9);
        assertEquals(300.0, model.centerWorldZ(), 1.0e-9);
        assertEquals(2.0, model.pixelsPerBlock(), 1.0e-9);
        assertFalse(model.demand(0).visible().isEmpty());
    }
    @Test
    void zoomSelectsCoarserLodDeterministically() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(800, 600);
        model.centerOn(0.0, 0.0);

        assertEquals(RenderLod.LOD_0, model.demand(0).lod());

        model.zoomAt(0.125, 400, 300);

        assertEquals(RenderLod.LOD_3, model.demand(0).lod());
    }


    @Test
    void zoomOutIsRejectedWhenItWouldExceedTileBudget() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(1400, 800);
        model.centerOn(0.0, 0.0);

        while (model.zoomAtWithinTileLimit(
                0.8,
                700,
                400,
                1,
                256
        )) {
            // Keep zooming out until the next step would exceed the budget.
        }

        WorldMapViewportDemand demand = model.demand(1);
        assertTrue(demand.visible().size() + demand.prefetch().size() <= 256);
    }

    @Test
    void zoomInRemainsPossibleWhenCurrentViewportAlreadyExceedsBudget() {
        WorldMapViewportModel model =
                new WorldMapViewportModel(new RenderTileLayout(4));
        model.setViewportSize(4000, 2500);
        model.centerOn(0.0, 0.0);
        WorldMapViewportDemand before = model.demand(1);
        int beforeCount = before.visible().size() + before.prefetch().size();

        assertTrue(model.zoomAtWithinTileLimit(
                2.0,
                2000,
                1250,
                1,
                16
        ));
        WorldMapViewportDemand after = model.demand(1);
        int afterCount = after.visible().size() + after.prefetch().size();

        assertTrue(afterCount < beforeCount);
    }

}
