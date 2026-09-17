package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.EnvironmentOverlayRenderer;
import cartographer.render.GeologyOverlayRenderer;
import cartographer.render.MapRenderer;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.OverlayRenderReport;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderedMap;
import cartographer.render.SystemMarkerOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.MultiActualBlockMapScanner;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RenderActualOreMapUseCase {

    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final MultiActualBlockMapScanner multiActualBlockMapScanner;
    private final OreChunkPositionPlanner oreChunkPositionPlanner;
    private final ActualOreOverlayPainter actualOreOverlayPainter;
    private final EnvironmentInterpreter environmentInterpreter = new EnvironmentInterpreter();
    private final GeologicProvinceInterpreter geologicProvinceInterpreter = new GeologicProvinceInterpreter();
    private final EnvironmentOverlayRenderer environmentOverlayRenderer = new EnvironmentOverlayRenderer();
    private final GeologyOverlayRenderer geologyOverlayRenderer = new GeologyOverlayRenderer();
    private final SystemMarkerOverlayRenderer systemMarkerOverlayRenderer = new SystemMarkerOverlayRenderer();
    private final MapChunkRenderWindowPlanner mapChunkRenderWindowPlanner =
            new MapChunkRenderWindowPlanner();
    private final MapChunkPositionPlanner mapChunkPositionPlanner =
            new MapChunkPositionPlanner();
    private final SurfaceFallbackChunkPlanner surfaceFallbackChunkPlanner =
            new SurfaceFallbackChunkPlanner();

    public RenderActualOreMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter
    ) {
        this(
                reader,
                metadataReader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                actualBlockMapScanner,
                actualOreOverlayPainter,
                new MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner()
        );
    }

    public RenderActualOreMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter,
            MultiActualBlockMapScanner multiActualBlockMapScanner
    ) {
        this(
                reader,
                metadataReader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                actualBlockMapScanner,
                actualOreOverlayPainter,
                multiActualBlockMapScanner,
                new OreChunkPositionPlanner()
        );
    }

    public RenderActualOreMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter,
            MultiActualBlockMapScanner multiActualBlockMapScanner,
            OreChunkPositionPlanner oreChunkPositionPlanner
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        Objects.requireNonNull(actualBlockMapScanner, "actualBlockMapScanner is required");
        this.actualOreOverlayPainter = Objects.requireNonNull(actualOreOverlayPainter, "actualOreOverlayPainter is required");
        this.multiActualBlockMapScanner = Objects.requireNonNull(
                multiActualBlockMapScanner,
                "multiActualBlockMapScanner is required"
        );
        this.oreChunkPositionPlanner = Objects.requireNonNull(
                oreChunkPositionPlanner,
                "oreChunkPositionPlanner is required"
        );
    }

    public RenderActualOreMapResult execute(RenderActualOreMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public RenderActualOreMapResult execute(
            RenderActualOreMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        WorldMetadata metadata = metadataReader.read(request.savePath());
        WorldPosition player = reader.readPlayerPosition(request.savePath());
        WorldPosition center = request.center().orElse(player);
        RenderOptions options = new RenderOptions(
                request.radius(),
                request.pixelsPerBlock(),
                request.style(),
                request.layers()
        );

        HomeState home = absoluteHome(request.savePath(), metadata);
        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        boolean surfaceDataRequired =
                options.layers().contains(RenderLayer.SURFACE)
                        || options.layers().contains(RenderLayer.SOIL_FERTILITY);
        int centerWorldX = (int) Math.round(center.x());
        int centerWorldZ = (int) Math.round(center.z());
        List<MapChunkCoordinate> renderMapChunkCoordinates =
                mapChunkRenderWindowPlanner.plan(
                        metadata,
                        center,
                        request.radius()
                );
        List<MapChunkCoordinate> surfaceMapChunkCoordinates = surfaceDataRequired
                ? mapChunkPositionPlanner.plan(
                        metadata,
                        centerWorldX,
                        centerWorldZ,
                        request.radius()
                )
                : List.of();
        Set<MapChunkCoordinate> renderMapChunkSet =
                new HashSet<>(renderMapChunkCoordinates);
        Set<MapChunkCoordinate> surfaceSearchSet =
                new HashSet<>(surfaceMapChunkCoordinates);
        Set<MapChunkCoordinate> directReadSet =
                new LinkedHashSet<>(renderMapChunkCoordinates);
        directReadSet.addAll(surfaceMapChunkCoordinates);
        List<MapChunkCoordinate> directReadCoordinates = directReadSet.stream()
                .sorted(
                        Comparator.comparingInt(MapChunkCoordinate::z)
                                .thenComparingInt(MapChunkCoordinate::x)
                )
                .toList();
        MapTerrainPreparation.Builder terrainBuilder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        renderMapChunkCoordinates.size(),
                        progress
                );
        Map<Integer, BlockInfo> surfaceRegistry = surfaceDataRequired
                ? reader.readBlockRegistry(request.savePath())
                : Map.of();
        SurfaceStreamingSession surfaceSession =
                surfaceDataRequired
                        ? SurfaceStreamingSession.begin(
                                metadata,
                                centerWorldX,
                                centerWorldZ,
                                request.radius(),
                                surfaceMapChunkCoordinates,
                                surfaceRegistry,
                                true,
                                true
                        )
                        : null;
        reader.forEachMapChunkByCoordinate(
                request.savePath(),
                directReadCoordinates,
                mapChunkDiagnostics,
                mapChunk -> {
                    if (renderMapChunkSet.contains(mapChunk.coordinate())) {
                        terrainBuilder.accept(mapChunk);
                    }
                    if (surfaceDataRequired
                            && surfaceSearchSet.contains(mapChunk.coordinate())) {
                        surfaceSession.acceptMapChunk(mapChunk);
                    }
                },
                progress
        );
        MapTerrainPreparation terrain = terrainBuilder.finish();
        SurfaceMapScanResult compactSurface = surfaceDataRequired
                ? readCompactSurface(
                        request.savePath(),
                        metadata,
                        surfaceSession,
                        surfaceRegistry,
                        chunkDiagnostics,
                        progress
                )
                : emptySurface(metadata, center, request.radius());
        RenderedMap rendered = renderer.render(
                center, player, home, terrain, compactSurface.map(),
                compactSurface.registry(), options, progress
        );

        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        List<ServerMapRegion> mapRegions = hasMapRegionOverlay(options)
                ? readMapRegionsWithProgress(
                        request.savePath(), mapRegionDiagnostics, progress
                )
                : List.of();
        if (hasMapRegionOverlay(options)) {
            progress.start("Painting map-region overlays");
        }
        OverlayRenderReport environmentOverlay = drawEnvironmentOverlay(
                rendered, center, request.radius(), options, mapRegions
        );
        OverlayRenderReport geologyOverlay = drawGeologyOverlay(
                rendered, center, request.radius(), options, mapRegions
        );

        ReadDiagnostics actualOreDiagnostics = new ReadDiagnostics();
        List<ActualOreOverlayResult> actualOreOverlays = drawActualOreOverlays(
                request, rendered, center, metadata, actualOreDiagnostics, progress
        );

        if ((hasMapRegionOverlay(options) || !actualOreOverlays.isEmpty())
                && options.layers().contains(RenderLayer.MARKERS)) {
            systemMarkerOverlayRenderer.draw(
                    rendered.image(), center, player, home, request.radius()
            );
        }

        int userMarkersDrawn = 0;
        if (options.layers().contains(RenderLayer.MARKERS)) {
            List<cartographer.marker.UserMarker> markers = markerStore.load(request.savePath());
            if (!markers.isEmpty()) {
                userMarkersDrawn = userMarkerRenderer.draw(
                        rendered.image(), center, request.radius(), markers, metadata
                );
            }
        }

        return new RenderActualOreMapResult(
                rendered.image(), rendered.geometry(), rendered.report(),
                compatibilitySurface(compactSurface), compactSurface,
                environmentOverlay, geologyOverlay,
                actualOreOverlays.isEmpty()
                        ? Optional.empty()
                        : Optional.of(actualOreOverlays.getFirst().map()),
                mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn, actualOreOverlays
        );
    }

    private List<ServerMapRegion> readMapRegionsWithProgress(
            Path savePath,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start("Reading map regions");
        return reader.readMapRegions(savePath, diagnostics);
    }

    private List<ActualOreOverlayResult> drawActualOreOverlays(
            RenderActualOreMapRequest request,
            RenderedMap rendered,
            WorldPosition center,
            WorldMetadata metadata,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        List<ActualOreOverlaySpec> specs = request.oreOverlays();
        if (specs.isEmpty()) {
            return List.of();
        }
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        int centerX = (int) Math.round(center.x());
        int centerZ = (int) Math.round(center.z());
        List<ActualBlockMatchSpec> matches = specs.stream()
                .map(spec -> new ActualBlockMatchSpec(spec.match(), spec.matchMode()))
                .toList();
        MultiActualBlockMapScanner.StreamingSession session =
                multiActualBlockMapScanner.begin(
                        registry,
                        centerX,
                        centerZ,
                        request.radius(),
                        matches,
                        request.yFilter()
                );
        int[] wantedBlockIds = session.wantedBlockIds();
        if (wantedBlockIds.length != 0) {
            List<cartographer.model.ChunkPosition> positions = oreChunkPositionPlanner.plan(
                    metadata,
                    centerX,
                    centerZ,
                    request.radius(),
                    request.yFilter()
            );
            reader.forEachChunkByPositionMatchingBlockIdsAdaptive(
                    request.savePath(),
                    positions,
                    wantedBlockIds,
                    diagnostics,
                    session::accept,
                    progress
            );
        }
        List<ActualBlockMap> maps = session.finish();

        List<ActualOreOverlayResult> results = new java.util.ArrayList<>();
        for (int index = 0; index < specs.size(); index++) {
            results.add(
                    new ActualOreOverlayResult(
                            specs.get(index),
                            maps.get(index)
                    )
            );
        }
        List<ActualOreOverlayResult> immutable = List.copyOf(results);
        actualOreOverlayPainter.paint(
                rendered.image(),
                immutable,
                center,
                request.radius()
        );
        return immutable;
    }

    private SurfaceMapScanResult readCompactSurface(
            java.nio.file.Path savePath,
            WorldMetadata metadata,
            SurfaceStreamingSession surfaceSession,
            Map<Integer, BlockInfo> registry,
            ReadDiagnostics chunkDiagnostics,
            ProgressReporter progress
    ) {
        SurfaceRainHeightPlan rainPlan = surfaceSession.finishPlanning();
        ChunkStreamStats fastChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!rainPlan.chunkPositions().isEmpty()) {
            fastChunkStats = reader.forEachChunkByPositionAdaptive(
                    savePath,
                    rainPlan.chunkPositions(),
                    chunkDiagnostics,
                    surfaceSession::acceptFastChunk,
                    progress
            );
        }
        List<MapChunkCoordinate> fallbackMapChunks = surfaceSession.fallbackMapChunks();
        List<ChunkPosition> fallbackPositions = surfaceFallbackChunkPlanner.plan(
                metadata,
                fallbackMapChunks
        );
        ChunkStreamStats fallbackChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!fallbackPositions.isEmpty()) {
            fallbackChunkStats = reader.forEachChunkByPositionAdaptive(
                    savePath,
                    fallbackPositions,
                    chunkDiagnostics,
                    surfaceSession::acceptFallbackChunk,
                    progress
            );
        }
        SurfaceRainHeightScanResult result = surfaceSession.finish();
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        SurfaceMap map = result.surface();
        SurfaceRainHeightDiagnosticCounters diagnostics = result.diagnostics();
        return new SurfaceMapScanResult(
                map,
                registry,
                chunksScanned,
                diagnostics.columnsScanned(),
                diagnostics.emptyColumns(),
                diagnostics.liquidUnavailableColumns()
        );
    }

    private SurfaceMapScanResult emptySurface(
            WorldMetadata metadata,
            WorldPosition center,
            int radius
    ) {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                center.x(), center.z(), radius, metadata
        );
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        return new SurfaceMapScanResult(
                accumulator.finish(), Map.of(), 0, 0, 0, 0
        );
    }

    private SurfaceScanResult compatibilitySurface(SurfaceMapScanResult surface) {
        return new SurfaceScanResult(
                List.of(),
                surface.chunksScanned(),
                surface.columnsScanned(),
                surface.emptyColumns(),
                surface.liquidUnavailableColumns()
        );
    }

    private List<ServerMapRegion> mapRegions(
            java.nio.file.Path savePath,
            RenderOptions options,
            ReadDiagnostics diagnostics
    ) {
        return hasMapRegionOverlay(options)
                ? reader.readMapRegions(savePath, diagnostics)
                : List.of();
    }

    private OverlayRenderReport drawEnvironmentOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            List<ServerMapRegion> regions
    ) {
        if (!options.layers().contains(RenderLayer.ENVIRONMENT)) {
            return OverlayRenderReport.none();
        }
        List<EnvironmentProfile> profiles = regions.stream()
                .map(environmentInterpreter::interpret)
                .toList();
        return environmentOverlayRenderer.draw(rendered.image(), center, radius, profiles);
    }

    private OverlayRenderReport drawGeologyOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            List<ServerMapRegion> regions
    ) {
        if (!options.layers().contains(RenderLayer.GEOLOGY)) {
            return OverlayRenderReport.none();
        }
        List<GeologicProvinceSummary> summaries = regions.stream()
                .map(geologicProvinceInterpreter::summarize)
                .flatMap(Optional::stream)
                .toList();
        return geologyOverlayRenderer.draw(rendered.image(), center, radius, summaries);
    }

    private boolean hasMapRegionOverlay(RenderOptions options) {
        return options.layers().contains(RenderLayer.ENVIRONMENT)
                || options.layers().contains(RenderLayer.GEOLOGY);
    }

    private HomeState absoluteHome(
            java.nio.file.Path savePath,
            WorldMetadata metadata
    ) {
        Optional<HomeLocation> displayHome = homeStore.load(savePath);
        if (displayHome.isEmpty()) {
            return HomeState.absent();
        }
        HomeLocation location = displayHome.orElseThrow();
        cartographer.model.WorldPosition absolute = metadata.toAbsolute(
                new cartographer.model.DisplayPosition(location.x(), 0.0, location.z())
        );
        return HomeState.present(new HomeLocation(absolute.x(), absolute.z()));
    }
}
