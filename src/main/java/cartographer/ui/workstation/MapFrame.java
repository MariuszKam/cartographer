package cartographer.ui.workstation;

import cartographer.application.ActualOreOverlayResult;
import cartographer.application.MapDecorationState;
import cartographer.application.PreparedMapData;
import cartographer.geology.rock.RockMap;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderLayer;
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
        Optional<MapDecorationState> decorationState
) {
    public MapFrame {
        savePath = Objects.requireNonNull(savePath, "savePath is required")
                .toAbsolutePath()
                .normalize();
        tool = Objects.requireNonNull(tool, "tool is required");
        geometry = Objects.requireNonNull(geometry, "geometry is required");
        preparedMapData = Objects.requireNonNull(
                preparedMapData,
                "prepared map data option is required"
        );
        actualOreOverlays = List.copyOf(
                Objects.requireNonNull(actualOreOverlays, "actual ore overlays are required")
        );
        surfaceAnalysis = Objects.requireNonNull(
                surfaceAnalysis,
                "surface analysis option is required"
        );
        rockMap = Objects.requireNonNull(rockMap, "rock map option is required");
        decorationState = Objects.requireNonNull(
                decorationState,
                "decoration state option is required"
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
            case GEOLOGY -> {
                if (rockMap.isEmpty()) {
                    throw new IllegalArgumentException("geology frame requires RockMap");
                }
                if (preparedMapData.isPresent()
                        || !actualOreOverlays.isEmpty()
                        || surfaceAnalysis.isPresent()
                        || decorationState.isPresent()) {
                    throw new IllegalArgumentException(
                            "geology frame contains incompatible retained state"
                    );
                }
            }
            case COVERAGE -> {
                if (preparedMapData.isPresent()
                        || !actualOreOverlays.isEmpty()
                        || surfaceAnalysis.isPresent()
                        || rockMap.isPresent()
                        || decorationState.isPresent()) {
                    throw new IllegalArgumentException(
                            "coverage frame must retain geometry only"
                    );
                }
            }
            case PROSPECTING -> throw new IllegalArgumentException(
                    "prospecting does not produce a displayed map frame"
            );
        }
    }

    public static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared
    ) {
        return map(savePath, geometry, prepared, Optional.empty());
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
                Optional.of(Objects.requireNonNull(decorations, "decorations are required"))
        );
    }

    private static MapFrame map(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            Optional<MapDecorationState> decorations
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.MAP,
                geometry,
                Optional.of(Objects.requireNonNull(prepared, "prepared is required")),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                decorations
        );
    }

    public static MapFrame ore(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            List<ActualOreOverlayResult> overlays
    ) {
        return ore(savePath, geometry, prepared, overlays, Optional.empty());
    }

    public static MapFrame ore(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            List<ActualOreOverlayResult> overlays,
            MapDecorationState decorations
    ) {
        return ore(
                savePath,
                geometry,
                prepared,
                overlays,
                Optional.of(Objects.requireNonNull(decorations, "decorations are required"))
        );
    }

    private static MapFrame ore(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            List<ActualOreOverlayResult> overlays,
            Optional<MapDecorationState> decorations
    ) {
        return new MapFrame(
                savePath,
                WorkstationTool.ORE,
                geometry,
                Optional.of(Objects.requireNonNull(prepared, "prepared is required")),
                overlays,
                Optional.empty(),
                Optional.empty(),
                decorations
        );
    }

    public static MapFrame surface(
            Path savePath,
            MapViewportGeometry geometry,
            PreparedMapData prepared,
            SurfaceRenderAnalysis analysis
    ) {
        return surface(
                savePath,
                geometry,
                prepared,
                analysis,
                Optional.empty()
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
                decorations
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
                Optional.empty()
        );
    }

    public boolean supportsLocalRecomposition(Set<RenderLayer> layers) {
        Objects.requireNonNull(layers, "layers are required");
        Set<RenderLayer> supported = Set.of(
                RenderLayer.TERRAIN,
                RenderLayer.SURFACE,
                RenderLayer.SOIL_FERTILITY,
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
        if (initial.contains(RenderLayer.ENVIRONMENT)
                || initial.contains(RenderLayer.GEOLOGY)) {
            return false;
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
        return !layers.contains(RenderLayer.SOIL_FERTILITY)
                || surfaceDataPrepared;
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
