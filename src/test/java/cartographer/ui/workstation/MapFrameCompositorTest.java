package cartographer.ui.workstation;

import cartographer.application.MapDecorationState;
import cartographer.application.PreparedMapData;
import cartographer.application.PreparedSurfaceData;
import cartographer.application.SurfaceDataRequirement;
import cartographer.application.ProgressReporter;
import cartographer.application.RenderDataCacheReport;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.save.ReadDiagnostics;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MapFrameCompositorTest {

    @Test
    void recomposesFromRetainedStateAndCanToggleSystemMarkersLocally() {
        PreparedMapData prepared = prepared();
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                64, 64, 16, 16, 48, 48
        );
        MapFrame frame = MapFrame.map(
                Path.of("world.vcdbs"),
                geometry,
                prepared,
                new MapDecorationState(HomeState.absent(), List.of())
        );
        MapFrameCompositor compositor = new MapFrameCompositor();

        BufferedImage withoutMarkers = compositor.recompose(
                frame,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                ProgressReporter.NONE
        );
        BufferedImage withMarkers = compositor.recompose(
                frame,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
                ProgressReporter.NONE
        );

        assertEquals(64, withoutMarkers.getWidth());
        assertEquals(64, withoutMarkers.getHeight());
        assertNotEquals(
                withoutMarkers.getRGB(32, 32),
                withMarkers.getRGB(32, 32)
        );
    }

    private PreparedMapData prepared() {
        WorldMetadata metadata = new WorldMetadata(64, 256, 64);
        WorldPosition center = new WorldPosition(32, 64, 32);
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS)
        );
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                center.x(), center.z(), 16, metadata
        );
        SurfaceMapScanResult surface = new SurfaceMapScanResult(
                new SurfaceTileAccumulator(layout).finish(),
                Map.of(),
                0, 0, 0, 0
        );
        return new PreparedMapData(
                metadata,
                center,
                center,
                options,
                MapTerrainPreparation.builder(
                        center,
                        options,
                        0,
                        ProgressReporter.NONE
                ).finish(),
                PreparedSurfaceData.fromExact(
                        surface,
                        center,
                        options,
                        SurfaceDataRequirement.RENDER
                ),
                Map.of(),
                new ReadDiagnostics(),
                new ReadDiagnostics(),
                RenderDataCacheReport.disabled("test")
        );
    }
}
