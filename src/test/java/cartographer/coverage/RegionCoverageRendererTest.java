package cartographer.coverage;

import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.WorldPosition;
import cartographer.render.MapViewportGeometry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCoverageRendererTest {

    @Test
    void nonEmptyCoverageExposesPaddedWorldGeometry() {
        RegionCoverageRenderResult result = new RegionCoverageRenderer().render(
                summary(),
                new WorldPosition(-16, 100, -48),
                HomeState.present(new HomeLocation(-24, -56))
        );

        MapViewportGeometry geometry = result.geometry().orElseThrow();
        assertEquals(result.image().getWidth(), geometry.imageWidth());
        assertEquals(result.image().getHeight(), geometry.imageHeight());
        assertEquals(20, geometry.contentX());
        assertEquals(20, geometry.contentY());
        assertEquals(28, geometry.contentWidth());
        assertEquals(28, geometry.contentHeight());
        assertEquals(-32, geometry.worldMinX());
        assertEquals(-64, geometry.worldMinZ());
        assertEquals(0, geometry.worldMaxXExclusive());
        assertEquals(-32, geometry.worldMaxZExclusive());
        assertEquals(20, geometry.absoluteWorldXToImageX(-32));
        assertEquals(48, geometry.absoluteWorldXToImageX(0));
        assertEquals(20, geometry.absoluteWorldZToImageY(-64));
        assertEquals(48, geometry.absoluteWorldZToImageY(-32));
        assertEquals(Color.RED.getRGB(), result.image().getRGB(34, 34));
        assertEquals(Color.CYAN.getRGB(), result.image().getRGB(27, 27));
    }

    @Test
    void emptyCoverageHasNoGeometry() {
        RegionCoverageRenderResult result = new RegionCoverageRenderer().render(
                RegionCoverageSummary.emptySummary(),
                null,
                HomeState.absent()
        );

        assertTrue(result.geometry().isEmpty());
        assertEquals(160, result.image().getWidth());
        assertEquals(160, result.image().getHeight());
    }

    @Test
    void cappedWideCoverageMapsCellsIntoAvailableContent() {
        RegionCoverageSummary summary = new RegionCoverageSummary(
                false,
                List.of(new RegionCoverageCell(
                        new MapRegionCoordinate(0, 0),
                        true,
                        0, 0, 32, 32,
                        0, 0, 32, 32
                )),
                0, 1014, 0, 0,
                1015, 1, 1015, 1015, 0, 100.0,
                0, 0, 1015 * 32, 32,
                0, 0, 1015 * 32, 32
        );

        RegionCoverageRenderResult result = new RegionCoverageRenderer().render(
                summary,
                null,
                HomeState.absent()
        );

        MapViewportGeometry geometry = result.geometry().orElseThrow();
        assertEquals(2048, result.image().getWidth());
        assertEquals(result.image().getWidth(), geometry.imageWidth());
        assertEquals(result.image().getHeight(), geometry.imageHeight());
        assertTrue(geometry.contentX() + geometry.contentWidth()
                <= geometry.imageWidth());
        assertTrue(geometry.contentY() + geometry.contentHeight()
                <= geometry.imageHeight());
        assertEquals(2008, geometry.contentWidth());
        assertEquals(2, geometry.contentHeight());
        assertEquals(20, geometry.contentX());
        assertEquals(20, geometry.contentY());
        assertEquals(20, geometry.absoluteWorldXToImageX(summary.worldMinX()));
        assertEquals(2028, geometry.absoluteWorldXToImageX(
                summary.worldMaxXExclusive()
        ));
        assertEquals(20, geometry.absoluteWorldZToImageY(summary.worldMinZ()));
        assertEquals(22, geometry.absoluteWorldZToImageY(
                summary.worldMaxZExclusive()
        ));
        assertEquals(summary.worldMinX(), geometry.worldMinX());
        assertEquals(summary.worldMinZ(), geometry.worldMinZ());
        assertEquals(summary.worldMaxXExclusive(), geometry.worldMaxXExclusive());
        assertEquals(summary.worldMaxZExclusive(), geometry.worldMaxZExclusive());
    }

    private RegionCoverageSummary summary() {
        return new RegionCoverageSummary(
                false,
                List.of(new RegionCoverageCell(
                        new MapRegionCoordinate(0, 0),
                        true,
                        -32, -64, 0, -32,
                        -544, -576, -512, -544
                )),
                0, 1, 0, 1,
                2, 2, 4, 1, 3, 25.0,
                -32, -64, 0, -32,
                -544, -576, -512, -544
        );
    }
}
