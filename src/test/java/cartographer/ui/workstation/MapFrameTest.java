package cartographer.ui.workstation;

import cartographer.application.MapDecorationState;
import cartographer.application.MapRegionOverlayState;
import cartographer.application.PreparedMapData;
import cartographer.application.PreparedSurfaceData;
import cartographer.application.RenderDataCacheReport;
import cartographer.application.SurfaceDataRequirement;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.progress.ProgressReporter;
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
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapFrameTest {

    @Test
    void retainedBoundedAnalysisFrameKeepsCompactStateWithoutRasterOrSourceResources() {
        PreparedMapData prepared = prepared(Set.of(RenderLayer.TERRAIN));
        MapFrame frame = MapFrame.ore(
                Path.of("world.vcdbs"),
                geometry(),
                prepared,
                List.of(),
                decorations(true),
                emptyRegionState()
        );

        assertEquals(WorkstationTool.ORE, frame.tool());
        assertSame(prepared, frame.preparedMapData().orElseThrow());
        assertTrue(frame.actualOreOverlays().isEmpty());
        assertTrue(frame.surfaceAnalysis().isEmpty());
        assertTrue(frame.rockMap().isEmpty());

        Set<Class<?>> forbidden = Set.of(
                BufferedImage.class,
                Connection.class,
                cartographer.save.SaveSession.class,
                cartographer.model.ParsedChunk.class
        );
        for (var component : MapFrame.class.getRecordComponents()) {
            assertFalse(
                    forbidden.stream().anyMatch(
                            type -> type.isAssignableFrom(component.getType())
                    ),
                    "MapFrame must not directly retain "
                            + component.getType().getName()
            );
        }
    }

    @Test
    void progressiveMapCannotBeRepresentedByBoundedMapFrame() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapFrame(
                        Path.of("world.vcdbs"),
                        WorkstationTool.MAP,
                        geometry(),
                        Optional.of(prepared(Set.of(RenderLayer.TERRAIN))),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(decorations(true)),
                        Optional.of(emptyRegionState())
                )
        );
    }

    @Test
    void localRecompositionRemainsAvailableForBoundedAnalysisFrames() {
        MapFrame rich = oreFrame(
                "rich.vcdbs",
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                decorations(true),
                emptyRegionState()
        );
        assertTrue(rich.supportsLocalRecomposition(Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.MARKERS
        )));
        assertFalse(rich.supportsLocalRecomposition(Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.SOIL_FERTILITY,
                RenderLayer.MARKERS
        )));

        MapFrame soilPrepared = oreFrame(
                "soil.vcdbs",
                Set.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.SURFACE,
                        RenderLayer.SOIL_FERTILITY
                ),
                decorations(true),
                emptyRegionState()
        );
        assertTrue(soilPrepared.supportsLocalRecomposition(Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.SOIL_FERTILITY
        )));

        MapFrame unavailableMarkers = oreFrame(
                "markers-unavailable.vcdbs",
                Set.of(RenderLayer.TERRAIN),
                decorations(false),
                emptyRegionState()
        );
        assertFalse(unavailableMarkers.supportsLocalRecomposition(
                Set.of(RenderLayer.TERRAIN, RenderLayer.MARKERS)
        ));

        MapFrame environmentPrepared = oreFrame(
                "environment.vcdbs",
                Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT),
                decorations(true),
                new MapRegionOverlayState(
                        Optional.of(List.of()),
                        Optional.empty()
                )
        );
        assertTrue(environmentPrepared.supportsLocalRecomposition(
                Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT)
        ));
        assertFalse(environmentPrepared.supportsLocalRecomposition(
                Set.of(RenderLayer.TERRAIN, RenderLayer.GEOLOGY)
        ));
    }

    @Test
    void retainedPreparedMapReuseStillRequiresMatchingBoundedAnalysisGeometry() {
        Path savePath = Path.of("reuse.vcdbs");
        MapFrame frame = MapFrame.ore(
                savePath,
                geometry(),
                prepared(Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE)),
                List.of(),
                decorations(true),
                emptyRegionState()
        );

        assertTrue(frame.canReusePreparedMap(
                savePath,
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Optional.empty(),
                true
        ));
        assertFalse(frame.canReusePreparedMap(
                savePath,
                32,
                1,
                RenderStyle.TOPOGRAPHIC,
                Optional.empty(),
                true
        ));
    }

    @Test
    void stateReplacesAndInvalidatesCurrentBoundedFrame() {
        MapFrameState state = new MapFrameState();
        MapFrame first = MapFrame.coverage(
                Path.of("first.vcdbs"),
                MapViewportGeometry.fullImage(64, 64, 0, 0, 64, 64)
        );
        MapFrame second = MapFrame.coverage(
                Path.of("second.vcdbs"),
                MapViewportGeometry.fullImage(32, 32, 0, 0, 32, 32)
        );

        assertTrue(state.current().isEmpty());
        state.retain(first);
        assertSame(first, state.current().orElseThrow());
        state.retain(second);
        assertSame(second, state.current().orElseThrow());
        state.clear();
        assertTrue(state.current().isEmpty());
    }

    private MapFrame oreFrame(
            String save,
            Set<RenderLayer> layers,
            MapDecorationState decorations,
            MapRegionOverlayState regionState
    ) {
        return MapFrame.ore(
                Path.of(save),
                geometry(),
                prepared(layers),
                List.of(),
                decorations,
                regionState
        );
    }

    private MapViewportGeometry geometry() {
        return MapViewportGeometry.fullImage(
                64, 64, 16, 16, 48, 48
        );
    }

    private MapDecorationState decorations(boolean available) {
        return new MapDecorationState(
                HomeState.absent(),
                List.of(),
                available
        );
    }

    private MapRegionOverlayState emptyRegionState() {
        return new MapRegionOverlayState(
                Optional.empty(),
                Optional.empty()
        );
    }

    private PreparedMapData prepared(Set<RenderLayer> layers) {
        WorldMetadata metadata = new WorldMetadata(64, 256, 64);
        WorldPosition center = new WorldPosition(32, 64, 32);
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                layers
        );
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                center.x(), center.z(), 16, metadata
        );
        SurfaceMapScanResult surface = new SurfaceMapScanResult(
                new SurfaceTileAccumulator(layout).finish(),
                Map.of(),
                0, 0, 0, 0
        );
        PreparedSurfaceData preparedSurface =
                layers.contains(RenderLayer.SOIL_FERTILITY)
                        ? PreparedSurfaceData.fromExact(
                                surface,
                                center,
                                options,
                                SurfaceDataRequirement.ANALYSIS
                        )
                        : layers.contains(RenderLayer.SURFACE)
                        ? PreparedSurfaceData.fromExact(
                                surface,
                                center,
                                options,
                                SurfaceDataRequirement.RENDER
                        )
                        : PreparedSurfaceData.none(center, options);
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
                preparedSurface,
                Map.of(),
                new ReadDiagnostics(),
                new ReadDiagnostics(),
                RenderDataCacheReport.disabled("test")
        );
    }
}
