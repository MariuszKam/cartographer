package cartographer.ui.workstation;

import cartographer.application.MapDecorationState;
import cartographer.application.MapRegionOverlayState;
import cartographer.application.PreparedMapData;
import cartographer.application.PreparedSurfaceData;
import cartographer.application.SurfaceDataRequirement;
import cartographer.application.ProgressReporter;
import cartographer.application.RenderDataCacheReport;
import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockIdentity;
import cartographer.geology.rock.RockMap;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderLayer;
import cartographer.render.MapViewportGeometry;
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

import static org.junit.jupiter.api.Assertions.*;

class MapFrameTest {

    @Test
    void retainedMapFrameKeepsCompactStateWithoutRasterOrSourceResources() {
        PreparedMapData prepared = prepared();
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                64, 64, 0, 0, 64, 64
        );

        MapFrame frame = MapFrame.map(
                Path.of("world.vcdbs"),
                geometry,
                prepared
        );

        assertEquals(WorkstationTool.MAP, frame.tool());
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
                    forbidden.stream().anyMatch(type ->
                            type.isAssignableFrom(component.getType())),
                    "MapFrame must not directly retain " + component.getType().getName()
            );
        }
    }

    @Test
    void localRecompositionRequiresRetainedDataForEnabledLayers() {
        MapDecorationState decorations =
                new MapDecorationState(HomeState.absent(), List.of());
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                64, 64, 16, 16, 48, 48
        );

        MapFrame rich = MapFrame.map(
                Path.of("rich.vcdbs"),
                geometry,
                prepared(Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE)),
                decorations
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

        MapFrame soilPrepared = MapFrame.map(
                Path.of("soil.vcdbs"),
                geometry,
                prepared(Set.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.SURFACE,
                        RenderLayer.SOIL_FERTILITY
                )),
                decorations
        );
        assertTrue(soilPrepared.supportsLocalRecomposition(Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.SOIL_FERTILITY
        )));

        MapFrame sparse = MapFrame.map(
                Path.of("sparse.vcdbs"),
                geometry,
                prepared(Set.of(RenderLayer.MARKERS)),
                decorations
        );
        assertTrue(sparse.supportsLocalRecomposition(Set.of(RenderLayer.MARKERS)));
        assertFalse(sparse.supportsLocalRecomposition(Set.of(RenderLayer.TERRAIN)));
        assertFalse(sparse.supportsLocalRecomposition(Set.of(RenderLayer.SURFACE)));
        assertFalse(sparse.supportsLocalRecomposition(Set.of(RenderLayer.SOIL_FERTILITY)));

        MapFrame unavailableMarkers = MapFrame.map(
                Path.of("markers-unavailable.vcdbs"),
                geometry,
                prepared(Set.of(RenderLayer.TERRAIN)),
                new MapDecorationState(HomeState.absent(), List.of(), false)
        );
        assertTrue(unavailableMarkers.supportsLocalRecomposition(
                Set.of(RenderLayer.TERRAIN)
        ));
        assertFalse(unavailableMarkers.supportsLocalRecomposition(
                Set.of(RenderLayer.TERRAIN, RenderLayer.MARKERS)
        ));

        MapFrame environmentPrepared = MapFrame.map(
                Path.of("environment.vcdbs"),
                geometry,
                prepared(Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT)),
                decorations,
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
    void retainedPreparedMapReuseRequiresMatchingGeometryAndSurfaceAvailability() {
        MapDecorationState decorations =
                new MapDecorationState(HomeState.absent(), List.of());
        MapFrame frame = MapFrame.map(
                Path.of("reuse.vcdbs"),
                MapViewportGeometry.fullImage(64, 64, 16, 16, 48, 48),
                prepared(Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE)),
                decorations
        );

        assertTrue(frame.canReusePreparedMap(
                Path.of("reuse.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Optional.empty(),
                true
        ));
        assertFalse(frame.canReusePreparedMap(
                Path.of("reuse.vcdbs"),
                32,
                1,
                RenderStyle.TOPOGRAPHIC,
                Optional.empty(),
                true
        ));

        MapFrame withoutSurface = MapFrame.map(
                Path.of("reuse.vcdbs"),
                MapViewportGeometry.fullImage(64, 64, 16, 16, 48, 48),
                prepared(Set.of(RenderLayer.TERRAIN)),
                decorations
        );
        assertFalse(withoutSurface.canReusePreparedMap(
                Path.of("reuse.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Optional.empty(),
                true
        ));
    }

    @Test
    void prospectingFrameRetainsOnlyCompactRockMapState() {
        RockIdentity granite = new RockIdentity(
                7,
                "game:rock-granite",
                "game",
                "granite"
        );
        RockMap rockMap = new RockMap(
                new WorldPosition(32, 0, 32),
                16,
                List.of(RockColumnSample.observed(
                        32,
                        32,
                        granite,
                        5
                ))
        );
        MapFrame frame = MapFrame.prospecting(
                Path.of("prospecting.vcdbs"),
                MapViewportGeometry.fullImage(
                        33,
                        33,
                        16,
                        16,
                        49,
                        49
                ),
                rockMap
        );

        assertEquals(WorkstationTool.PROSPECTING, frame.tool());
        assertSame(rockMap, frame.rockMap().orElseThrow());
        assertTrue(frame.preparedMapData().isEmpty());
        assertTrue(frame.actualOreOverlays().isEmpty());
        assertTrue(frame.surfaceAnalysis().isEmpty());
        assertTrue(frame.decorationState().isEmpty());
        assertTrue(frame.mapRegionOverlayState().isEmpty());
    }

    @Test
    void stateReplacesAndInvalidatesCurrentFrame() {
        MapFrameState state = new MapFrameState();
        MapFrame first = MapFrame.map(
                Path.of("first.vcdbs"),
                MapViewportGeometry.fullImage(64, 64, 0, 0, 64, 64),
                prepared()
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

    private PreparedMapData prepared() {
        return prepared(Set.of());
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
                        center, options, 0, ProgressReporter.NONE
                ).finish(),
                preparedSurface,
                Map.of(),
                new ReadDiagnostics(),
                new ReadDiagnostics(),
                RenderDataCacheReport.disabled("test")
        );
    }
}
