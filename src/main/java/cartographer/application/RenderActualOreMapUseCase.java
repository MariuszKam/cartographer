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
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
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
import cartographer.scanner.RainHeightSurfacePlan;
import cartographer.scanner.RainHeightSurfacePlanner;
import cartographer.scanner.RainHeightSurfaceScanResult;
import cartographer.scanner.RainHeightSurfaceScanner;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceFallbackMapChunks;
import cartographer.scanner.SurfaceFastPathMerger;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

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
    private final RainHeightSurfacePlanner rainHeightSurfacePlanner =
            new RainHeightSurfacePlanner();
    private final RainHeightSurfaceScanner rainHeightSurfaceScanner =
            new RainHeightSurfaceScanner();
    private final SurfaceFallbackMapChunks surfaceFallbackMapChunks =
            new SurfaceFallbackMapChunks();
    private final SurfaceFallbackChunkPlanner surfaceFallbackChunkPlanner =
            new SurfaceFallbackChunkPlanner();
    private final SurfaceFastPathMerger surfaceFastPathMerger =
            new SurfaceFastPathMerger();
    private final SurfaceScanner surfaceScanner = new SurfaceScanner();

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
        Objects.requireNonNull(request, "request is required");

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
        boolean surfaceEnabled = options.layers().contains(RenderLayer.SURFACE);
        int centerWorldX = (int) Math.round(center.x());
        int centerWorldZ = (int) Math.round(center.z());
        List<MapChunkCoordinate> renderMapChunkCoordinates =
                mapChunkRenderWindowPlanner.plan(
                        metadata,
                        center,
                        request.radius()
                );
        List<MapChunkCoordinate> surfaceMapChunkCoordinates = surfaceEnabled
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
        Set<MapChunkCoordinate> deliveredSurfaceMapChunks =
                new HashSet<>();
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
                        renderMapChunkCoordinates.size()
                );
        RainHeightSurfacePlanner.StreamingSession rainPlannerSession =
                surfaceEnabled
                        ? rainHeightSurfacePlanner.begin(
                                metadata,
                                centerWorldX,
                                centerWorldZ,
                                request.radius()
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
                    if (surfaceEnabled
                            && surfaceSearchSet.contains(mapChunk.coordinate())) {
                        deliveredSurfaceMapChunks.add(mapChunk.coordinate());
                        rainPlannerSession.accept(mapChunk);
                    }
                }
        );
        MapTerrainPreparation terrain = terrainBuilder.finish();
        SurfaceScanResult surface = surfaceEnabled
                ? readSurface(
                        request.savePath(),
                        metadata,
                        centerWorldX,
                        centerWorldZ,
                        request.radius(),
                        surfaceMapChunkCoordinates,
                        deliveredSurfaceMapChunks,
                        rainPlannerSession,
                        chunkDiagnostics
                )
                : new SurfaceScanResult(List.of(), 0, 0, 0, 0);
        RenderedMap rendered = renderer.render(
                center, player, home, terrain, surface.blocks(), options
        );

        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        List<ServerMapRegion> mapRegions = hasMapRegionOverlay(options)
                ? reader.readMapRegions(request.savePath(), mapRegionDiagnostics)
                : List.of();
        OverlayRenderReport environmentOverlay = drawEnvironmentOverlay(
                rendered, center, request.radius(), options, mapRegions
        );
        OverlayRenderReport geologyOverlay = drawGeologyOverlay(
                rendered, center, request.radius(), options, mapRegions
        );

        ReadDiagnostics actualOreDiagnostics = new ReadDiagnostics();
        List<ActualOreOverlayResult> actualOreOverlays = drawActualOreOverlays(
                request, rendered, center, metadata, actualOreDiagnostics
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
                rendered.image(), rendered.report(), surface,
                environmentOverlay, geologyOverlay,
                actualOreOverlays.isEmpty()
                        ? Optional.empty()
                        : Optional.of(actualOreOverlays.getFirst().map()),
                mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn, actualOreOverlays
        );
    }

    private List<ActualOreOverlayResult> drawActualOreOverlays(
            RenderActualOreMapRequest request,
            RenderedMap rendered,
            WorldPosition center,
            WorldMetadata metadata,
            ReadDiagnostics diagnostics
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
        List<ActualBlockMap> maps;
        if (wantedBlockIds.length == 0) {
            maps = session.finish();
        } else {
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
                    session::accept
            );
            maps = session.finish();
        }

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

    private SurfaceScanResult readSurface(
            java.nio.file.Path savePath,
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            List<MapChunkCoordinate> surfaceMapChunkCoordinates,
            Set<MapChunkCoordinate> deliveredSurfaceMapChunks,
            RainHeightSurfacePlanner.StreamingSession rainPlannerSession,
            ReadDiagnostics chunkDiagnostics
    ) {
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath);
        RainHeightSurfacePlan rainPlan = rainPlannerSession.finish();
        RainHeightSurfaceScanner.StreamingSession fastSession =
                rainHeightSurfaceScanner.begin(rainPlan, registry, true, true);
        ChunkStreamStats fastChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!rainPlan.chunkPositions().isEmpty()) {
            fastChunkStats = reader.forEachChunkByPositionAdaptive(
                    savePath,
                    rainPlan.chunkPositions(),
                    chunkDiagnostics,
                    fastSession::accept
            );
        }
        RainHeightSurfaceScanResult fastResult = fastSession.finish();
        List<MapChunkCoordinate> fallbackMapChunks = surfaceFallbackMapChunks.collect(
                surfaceMapChunkCoordinates,
                deliveredSurfaceMapChunks,
                rainPlan,
                fastResult
        );
        List<ChunkPosition> fallbackPositions = surfaceFallbackChunkPlanner.plan(
                metadata,
                fallbackMapChunks
        );
        List<ParsedChunk> fallbackChunks = new ArrayList<>();
        ChunkStreamStats fallbackChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!fallbackPositions.isEmpty()) {
            fallbackChunkStats = reader.forEachChunkByPositionAdaptive(
                    savePath,
                    fallbackPositions,
                    chunkDiagnostics,
                    fallbackChunks::add
            );
        }
        SurfaceScanResult fallbackSurface = fallbackMapChunks.isEmpty()
                ? new SurfaceScanResult(List.of(), 0, 0, 0, 0)
                : surfaceScanner.scan(fallbackChunks, registry, true);
        int healthyFastColumns = (int) rainPlan.targets().stream()
                .filter(target -> !fallbackMapChunks.contains(target.mapChunkCoordinate()))
                .count();
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        int columnsScanned = Math.addExact(
                healthyFastColumns,
                fallbackSurface.columnsScanned()
        );
        List<SurfaceBlock> blocks = surfaceFastPathMerger.merge(
                fastResult.blocks(),
                fallbackSurface.blocks(),
                fallbackMapChunks,
                metadata,
                centerWorldX,
                centerWorldZ,
                radius
        );
        return new SurfaceScanResult(
                blocks,
                chunksScanned,
                columnsScanned,
                fallbackSurface.emptyColumns(),
                fallbackSurface.liquidUnavailableColumns()
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
