package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.model.ChunkPosition;
import cartographer.model.BlockInfo;
import cartographer.navigation.HomeStore;
import cartographer.render.MapRenderer;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderedMap;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceMaterialAnalyzer;
import cartographer.resource.SurfaceObjectSelectionAnalysis;
import cartographer.resource.SurfaceObjectAnalyzer;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.ChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceStreamingSession;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RenderSurfaceResourceMapUseCase {

    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final SaveSessionFactory sessionFactory;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final SurfaceMaterialAnalyzer surfaceMaterialAnalyzer;
    private final SurfaceObjectAnalyzer surfaceObjectAnalyzer;
    private final SurfaceResourceOverlayRenderer overlayRenderer;
    private final MapChunkRenderWindowPlanner mapChunkRenderWindowPlanner =
            new MapChunkRenderWindowPlanner();
    private final MapChunkPositionPlanner mapChunkPositionPlanner = new MapChunkPositionPlanner();

    public RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceMaterialAnalyzer surfaceMaterialAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer
    ) {
        this(
                reader,
                metadataReader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                ),
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                surfaceMaterialAnalyzer,
                overlayRenderer
        );
    }

    public RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionFactory sessionFactory,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceMaterialAnalyzer surfaceMaterialAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        this.surfaceMaterialAnalyzer = Objects.requireNonNull(
                surfaceMaterialAnalyzer,
                "surfaceMaterialAnalyzer is required"
        );
        this.surfaceObjectAnalyzer = new SurfaceObjectAnalyzer();
        this.overlayRenderer = Objects.requireNonNull(overlayRenderer, "overlayRenderer is required");
    }

    public RenderSurfaceResourceMapResult execute(RenderSurfaceResourceMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public RenderSurfaceResourceMapResult execute(
            RenderSurfaceResourceMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return execute(session, request, progress);
        }
    }

    public RenderSurfaceResourceMapResult execute(
            SaveSession session,
            RenderSurfaceResourceMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        WorldMetadata metadata = session.snapshot().metadata();
        WorldPosition player = reader.readPlayerPosition(session, progress);
        WorldPosition center = request.center().orElse(player);
        int centerWorldX = (int) Math.round(center.x());
        int centerWorldZ = (int) Math.round(center.z());
        RenderOptions options = new RenderOptions(
                request.radius(),
                request.pixelsPerBlock(),
                request.style(),
                request.layers()
        );

        HomeState home = absoluteHome(request.savePath(), metadata);
        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        List<MapChunkCoordinate> renderMapChunkCoordinates =
                mapChunkRenderWindowPlanner.plan(
                        metadata,
                        center,
                        request.radius()
                );
        List<MapChunkCoordinate> surfaceMapChunkCoordinates =
                mapChunkPositionPlanner.plan(
                        metadata,
                        centerWorldX,
                        centerWorldZ,
                        request.radius()
                );
        Set<MapChunkCoordinate> surfaceSearchSet =
                new HashSet<>(surfaceMapChunkCoordinates);
        Set<MapChunkCoordinate> renderMapChunkSet =
                new HashSet<>(renderMapChunkCoordinates);
        Set<MapChunkCoordinate> directReadSet =
                new LinkedHashSet<>(renderMapChunkCoordinates);
        directReadSet.addAll(surfaceMapChunkCoordinates);
        List<MapChunkCoordinate> directReadCoordinates = directReadSet.stream()
                .sorted(
                        Comparator.comparingInt(MapChunkCoordinate::z)
                                .thenComparingInt(MapChunkCoordinate::x)
                )
                .toList();
        Map<Integer, BlockInfo> registry = session.snapshot().blockRegistry();
        SurfaceStreamingSession surfaceSession = SurfaceStreamingSession.begin(
                metadata,
                centerWorldX,
                centerWorldZ,
                request.radius(),
                surfaceMapChunkCoordinates,
                registry,
                true,
                true
        );
        Set<MapChunkCoordinate> deliveredSurfaceMapChunks =
                new HashSet<>();
        MapTerrainPreparation.Builder terrainBuilder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        renderMapChunkCoordinates.size(),
                        progress
                );
        reader.forEachMapChunkByCoordinate(
                session,
                directReadCoordinates,
                mapChunkDiagnostics,
                mapChunk -> {
                    if (renderMapChunkSet.contains(mapChunk.coordinate())) {
                        terrainBuilder.accept(mapChunk);
                    }
                    if (surfaceSearchSet.contains(mapChunk.coordinate())) {
                        deliveredSurfaceMapChunks.add(mapChunk.coordinate());
                        surfaceSession.acceptMapChunk(mapChunk);
                    }
                },
                progress
        );
        MapTerrainPreparation terrain = terrainBuilder.finish();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        SurfaceRainHeightPlan rainPlan = surfaceSession.finishPlanning();
        ChunkStreamStats fastChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!rainPlan.chunkPositions().isEmpty()) {
            fastChunkStats = reader.forEachChunkByPositionAdaptive(
                    session,
                    rainPlan.chunkPositions(),
                    chunkDiagnostics,
                    surfaceSession::acceptFastChunk,
                    progress
            );
        }
        List<MapChunkCoordinate> fallbackMapChunks = surfaceSession.fallbackMapChunks();
        List<ChunkPosition> fallbackChunkPositions = new cartographer.scanner.SurfaceFallbackChunkPlanner().plan(
                metadata,
                fallbackMapChunks
        );
        ChunkStreamStats fallbackChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!fallbackChunkPositions.isEmpty()) {
            fallbackChunkStats = reader.forEachChunkByPositionAdaptive(
                    session,
                    fallbackChunkPositions,
                    chunkDiagnostics,
                    surfaceSession::acceptFallbackChunk,
                    progress
            );
        }
        SurfaceRainHeightScanResult compact = surfaceSession.finish();
        SurfaceMap surfaceMap = compact.surface();
        SurfaceRainHeightDiagnosticCounters diagnostics = compact.diagnostics();
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        SurfaceMapScanResult surface = new SurfaceMapScanResult(
                surfaceMap,
                registry,
                chunksScanned,
                diagnostics.columnsScanned(),
                diagnostics.emptyColumns(),
                diagnostics.liquidUnavailableColumns()
        );
        SurfaceRenderAnalysis analysis;
        if (request.material().isPresent()) {
            analysis = surfaceMaterialAnalyzer.analyze(
                    surface,
                    request.material().orElseThrow(),
                    request.resourceDisplayName());
        } else {
            analysis = new SurfaceObjectSelectionAnalysis(
                    request.observedResources().stream()
                            .map(resource -> surfaceObjectAnalyzer.analyze(resource, registry))
                            .toList());
        }

        RenderedMap rendered = renderer.render(
                center,
                player,
                home,
                terrain,
                surface.map(),
                registry,
                options,
                progress
        );

        progress.start("Painting surface resource overlay");
        if (analysis instanceof SurfaceMaterialAnalysis materialAnalysis) {
            overlayRenderer.drawMaterial(rendered.image(), center, request.radius(),
                    materialAnalysis, player, home);
        } else if (analysis instanceof SurfaceObjectSelectionAnalysis objectAnalysis) {
            overlayRenderer.drawObjects(rendered.image(), center, request.radius(),
                    objectAnalysis, player, home);
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

        return new RenderSurfaceResourceMapResult(
                rendered.image(),
                rendered.geometry(),
                analysis,
                surface,
                rendered.report(),
                mapChunkDiagnostics,
                chunkDiagnostics,
                userMarkersDrawn
        );
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
        WorldPosition absolute = metadata.toAbsolute(
                new DisplayPosition(location.x(), 0.0, location.z())
        );
        return HomeState.present(new HomeLocation(absolute.x(), absolute.z()));
    }

}
