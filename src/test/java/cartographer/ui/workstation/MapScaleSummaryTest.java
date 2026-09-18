package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapScaleSummaryTest {
    @Test
    void describesR4096CoverageUsing4kRaster() {
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                4096,
                4096,
                -4096,
                -4096,
                4096,
                4096
        );

        assertEquals(
                "Area 8192×8192 blocks | Raster 4096×4096 px | 2.00 blk/px",
                MapScaleSummary.format(geometry)
        );
    }

    @Test
    void describesNonSquareEffectiveScaleExplicitly() {
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                100,
                50,
                0,
                0,
                200,
                200
        );

        assertEquals(
                "Area 200×200 blocks | Raster 100×50 px | 2.00×4.00 blk/px",
                MapScaleSummary.format(geometry)
        );
    }
}
