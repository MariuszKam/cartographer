package cartographer.ui.workstation;

import cartographer.render.ActualOreOverlayResult;
import cartographer.application.MapDecorationState;
import cartographer.application.MapRegionOverlayState;
import cartographer.application.PreparedMapData;
import cartographer.geology.rock.RockMap;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.model.WorldPosition;
import cartographer.resource.SurfaceRenderAnalysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Retained non-raster state for the map currently displayed by the Workstation.
 *
 * <p>The raster itself remains owned by {@link MapPanel}. A frame retains only
 * bounded/compact analysis state and geometry; it never owns a SaveSession,
 * JDBC connection, decoded source chunk collection, or duplicate BufferedImage.</p>
 */
public record MapFrame(
        Path savePath,
        WorkstationTool tool,
        MapViewportGeometry geometry,
        Optional<PreparedMapData> preparedMapData,
        List<ActualOreOverlayResult> actualOreOverlays,
        Optional<SurfaceRenderAnalysis> surfaceAnalysis,
        Optional<RockMap> rockMap,
        Optional<MapDecorationState> decorationState,
        Optional<MapRegionOverlayState> mapRegionOverlayState
) {
    public MapFrame {
        savePath = Objects.requireNonNull(savePath, "savePath is required")
                .toAbsolutePath()
                .normalize();
        Objects.requireNonNull(tool, "tool is required");
        Objects.requireNonNull(geometry, "geometry is required");
        Objects.requireNonNull(
                preparedMapData,
                "prepared map data option is required"
        );
        actualOreOverlays = List.copyOf(
                Objects.requireNonNull(actualOreOverlays, "actual ore overlays are required")
        );
        Objects.requireNonNull(
                surfaceAnalysis,
                "surface analysis option is required"
        );
        Objects.requireNonNull(rockMap, "rock map option is required");
        Objects.requireNonNull(
                decorationState,
                "decoration state option is required"
        );
        Objects.requireNonNull(
                mapRegionOverlayState,
                "map-region overlay state option is required"
        );

        switch (tool) {
            case MAP -> requirePreparedOnly(
                    preparedMapData,
                    actualOreOverlays,
                    surfaceAnalysis,
                    rockMap
            );
            case ORE -> {
                if (preparedMapData.isEmpty()) {
                    throw new IllegalArgumentException("ore frame requires prepared map data");
                }
                if (surfaceAnalysis.isPresent() || rockMap.isPresent()) {
                    throw new IllegalArgumentException("ore frame contains incompatible retained state");
                }
            }
            case SURFACE -> {
                if (preparedMapData.isEmpty() || surfaceAnalysis.isEmpty()) {
                    throw new IllegalArgumentException(
                            "surface frame requires prepared map data and analysis"
                    );
                }
                if (!actualOreOverlays.isEmpty() || rockMap.isPresent()) {
                    throw new IllegalArgumentException(
                            "surface frame contains incompatible retained state"
                    );
                }
            }
            case GEOLOGY, PROSPECTING -> {
                if (rockMap.isEmpty()) {
                    throw new IllegalArgumentException(
                            tool.name().toLowerCase() + " frame requires RockMap"
                    );
                }
                if (preparedMapData.isPresent()
                        || !actualOreOverlays.isEmpty()
                        || surfaceAnalysis.isPresent()
                        || decorationState.isPresent()
                        || mapRegionOverlayState.isPresent()) {
                    throw new IllegalArgumentException(
                            tool.name().toLowerCase()
                                    + " frame contains incompatible retained state"
                    );
                }
            }
            case COVERAGE -> {
                if (preparedMapData.isPresent()
                        || !actualOreOverlays.isEmpty()
                        || surfaceAnalysis.isPresent()
                        || rockMap.isPresent()
                        || decorationState.isPresent()
                        || mapRegionOverlayState.isPresent()) {
                    throw new IllegalArgumentException(
                            "coverage frame must retain geometry only"
                    );
                }
            }

        }
    }

    public static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared
    ) {
        return map(
                savePath,
                geometry,
                prepared,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            MapDecorationState decorations
    ) {
        return map(
                savePath,
                geometry,
                prepared,
                Optional.of(Objects.requireNonNull(decorations, "decorations are required")),
                Optional.empty()
        );
    }

    public static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            MapDecorationState decorations,
            MapRegionOverlayState mapRegionOverlays
    ) {
        return map(
                savePath,
                geometry,
                prepared,
                Optional.of(Objects.requireNonNull(decorations, "decorations are required")),
                Optional.of(Objects.requireNonNull(mapRegionOverlays, "mapRegionOverlays are required"))
        );
    }

    private static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            Optional<MapDecorationState> decorations,
            Optional<MapRegionOverlayState> mapRegionOverlays
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.MAP,
                geometry,
                Optional.of(Objects.requireNonNull(prepared, "prepared is required")),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                decorations,
                mapRegionOverlays
        );
    }

    public static MapFrame ore(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            List<ActualOreOverlayResult> overlays,
            MapDecorationState decorations,
            MapRegionOverlayState mapRegionOverlays
    ) {
        return ore(
                savePath,
                geometry,
                prepared,
                overlays,
                Optional.of(Objects.requireNonNull(decorations, "decorations are required")),
                Optional.of(Objects.requireNonNull(mapRegionOverlays, "mapRegionOverlays are required"))
        );
    }

    private static MapFrame ore(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            List<ActualOreOverlayResult> overlays,
            Optional<MapDecorationState> decorations,
            Optional<MapRegionOverlayState> mapRegionOverlays
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.ORE,
                geometry,
                Optional.of(Objects.requireNonNull(prepared, "prepared is required")),
                overlays,
                Optional.empty(),
                Optional.empty(),
                decorations,
                mapRegionOverlays
        );
    }

    public static MapFrame surface(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            SurfaceRenderAnalysis analysis,
            MapDecorationState decorations
    ) {
        return surface(
                savePath,
                geometry,
                prepared,
                analysis,
                Optional.of(Objects.requireNonNull(decorations, "decorations are required"))
        );
    }

    private static MapFrame surface(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            SurfaceRenderAnalysis analysis,
            Optional<MapDecorationState> decorations
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.SURFACE,
                geometry,
                Optional.of(Objects.requireNonNull(prepared, "prepared is required")),
                List.of(),
                Optional.of(Objects.requireNonNull(analysis, "analysis is required")),
                Optional.empty(),
                decorations,
                Optional.empty()
        );
    }

    public static MapFrame geology(
            Path savePath,
            MapViewportGeometry geometry,
            RockMap rockMap
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.GEOLOGY,
                geometry,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(rockMap, "rockMap is required")),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static MapFrame prospecting(
            Path savePath,
            MapViewportGeometry geometry,
            RockMap rockMap
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.PROSPECTING,
                geometry,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(rockMap, "rockMap is required")),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static MapFrame coverage(
            Path savePath,
            MapViewportGeometry geometry
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.COVERAGE,
                geometry,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public boolean canReusePreparedMap(
            Path requestedSavePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Optional<WorldPosition> requestedCenter,
            boolean requireSurfaceData
    ) {
        Objects.requireNonNull(requestedSavePath, "requestedSavePath is required");
        Objects.requireNonNull(style, "style is required");
        Objects.requireNonNull(requestedCenter, "requestedCenter is required");
        if (preparedMapData.isEmpty() || decorationState.isEmpty()) {
            return false;
        }
        if (!savePath.equals(requestedSavePath.toAbsolutePath().normalize())) {
            return false;
        }
        PreparedMapData prepared = preparedMapData.orElseThrow();
        if (prepared.options().radiusBlocks() != radius
                || prepared.options().pixelsPerBlock() != pixelsPerBlock
                || prepared.options().style() != style) {
            return false;
        }
        if (requestedCenter.isPresent()
                && !requestedCenter.orElseThrow().equals(prepared.center())) {
            return false;
        }
        return !requireSurfaceData || surfaceDataPrepared();
    }

    public boolean surfaceDataPrepared() {
        if (preparedMapData.isEmpty()) {
            return false;
        }
        if (tool == WorkstationTool.SURFACE) {
            return true;
        }
        Set<RenderLayer> initial = preparedMapData.orElseThrow().options().layers();
        return initial.contains(RenderLayer.SURFACE)
                || initial.contains(RenderLayer.SOIL_FERTILITY);
    }

    public boolean supportsLocalRecomposition(Set<RenderLayer> layers) {
        Objects.requireNonNull(layers, "layers are required");
        Set<RenderLayer> supported = Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.SOIL_FERTILITY,
                RenderLayer.ENVIRONMENT,
                RenderLayer.GEOLOGY,
                RenderLayer.MARKERS
        );
        if (!supported.containsAll(layers)
                || preparedMapData.isEmpty()
                || decorationState.isEmpty()
                || (tool != WorkstationTool.MAP
                && tool != WorkstationTool.ORE
                && tool != WorkstationTool.SURFACE)) {
            return false;
        }

        PreparedMapData prepared = preparedMapData.orElseThrow();
        MapDecorationState decorations = decorationState.orElseThrow();
        if (layers.contains(RenderLayer.MARKERS)
                && !decorations.userMarkersAvailable()) {
            return false;
        }

        Set<RenderLayer> initial = prepared.options().layers();
        if (layers.contains(RenderLayer.ENVIRONMENT)) {
            if (tool == WorkstationTool.SURFACE
                    || mapRegionOverlayState.isEmpty()
                    || !mapRegionOverlayState.orElseThrow().environmentPrepared()) {
                return false;
            }
        }
        if (layers.contains(RenderLayer.GEOLOGY)) {
            if (tool == WorkstationTool.SURFACE
                    || mapRegionOverlayState.isEmpty()
                    || !mapRegionOverlayState.orElseThrow().geologyPrepared()) {
                return false;
            }
        }
        boolean terrainDataPrepared =
                initial.contains(RenderLayer.TERRAIN)
                        || initial.contains(RenderLayer.SURFACE);
        boolean surfaceDataPrepared =
                tool == WorkstationTool.SURFACE
                        || initial.contains(RenderLayer.SURFACE)
                        || initial.contains(RenderLayer.SOIL_FERTILITY);

        if (layers.contains(RenderLayer.TERRAIN) && !terrainDataPrepared) {
            return false;
        }
        if (layers.contains(RenderLayer.SURFACE)
                && (!terrainDataPrepared || !surfaceDataPrepared)) {
            return false;
        }
        if (layers.contains(RenderLayer.SOIL_FERTILITY)) {
            return surfaceDataPrepared
                    && prepared.surface().analysis().isPresent();
        }
        return true;
    }

    private static void requirePreparedOnly(
            Optional<PreparedMapData> prepared,
            List<ActualOreOverlayResult> overlays,
            Optional<SurfaceRenderAnalysis> surfaceAnalysis,
            Optional<RockMap> rockMap
    ) {
        if (prepared.isEmpty()) {
            throw new IllegalArgumentException("map frame requires prepared map data");
        }
        if (!overlays.isEmpty() || surfaceAnalysis.isPresent() || rockMap.isPresent()) {
            throw new IllegalArgumentException("map frame contains incompatible retained state");
        }
    }
}
