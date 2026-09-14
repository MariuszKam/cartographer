package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
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
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectAnalyzer;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.save.ReadDiagnostics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
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

public class RenderSurfaceResourceMapUseCase {

    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final SurfaceScanner surfaceScanner;
    private final SurfaceMaterialAnalyzer surfaceMaterialAnalyzer;
    private final SurfaceObjectAnalyzer surfaceObjectAnalyzer;
    private final SurfaceResourceOverlayRenderer overlayRenderer;
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

    public RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceScanner surfaceScanner,
            SurfaceMaterialAnalyzer surfaceMaterialAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        this.surfaceScanner = Objects.requireNonNull(surfaceScanner, "surfaceScanner is required");
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
            ObservedSurfaceResource observedResource,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(observedResource, "observed resource is required");
        if (request.observedResource().isEmpty()
                || !request.observedResource().orElseThrow().equals(observedResource)) {
            throw new IllegalArgumentException(
                    "request and observed resource selection do not match"
            );
        }
        return execute(request, progress);
    }

    public RenderSurfaceResourceMapResult execute(
            RenderSurfaceResourceMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        WorldMetadata metadata = metadataReader.read(request.savePath());
        WorldPosition player = reader.readPlayerPosition(request.savePath());
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
        Set<MapChunkCoordinate> deliveredSurfaceMapChunks =
                new HashSet<>();
        MapTerrainPreparation.Builder terrainBuilder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        renderMapChunkCoordinates.size(),
                        progress
                );
        RainHeightSurfacePlanner.StreamingSession rainPlannerSession =
                rainHeightSurfacePlanner.begin(
                        metadata,
                        centerWorldX,
                        centerWorldZ,
                        request.radius()
                );
        reader.forEachMapChunkByCoordinate(
                request.savePath(),
                directReadCoordinates,
                mapChunkDiagnostics,
                mapChunk -> {
                    if (renderMapChunkSet.contains(mapChunk.coordinate())) {
                        terrainBuilder.accept(mapChunk);
                    }
                    if (surfaceSearchSet.contains(mapChunk.coordinate())) {
                        deliveredSurfaceMapChunks.add(mapChunk.coordinate());
                        rainPlannerSession.accept(mapChunk);
                    }
                },
                progress
        );
        MapTerrainPreparation terrain = terrainBuilder.finish();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        RainHeightSurfacePlan rainPlan = rainPlannerSession.finish();
        RainHeightSurfaceScanner.StreamingSession fastSession =
                rainHeightSurfaceScanner.begin(
                        rainPlan,
                        registry,
                        true,
                        true
                );
        ChunkStreamStats fastChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!rainPlan.chunkPositions().isEmpty()) {
            fastChunkStats = reader.forEachChunkByPositionAdaptive(
                    request.savePath(),
                    rainPlan.chunkPositions(),
                    chunkDiagnostics,
                    fastSession::accept,
                    progress
            );
        }
        RainHeightSurfaceScanResult fastResult = fastSession.finish();
        List<MapChunkCoordinate> fallbackMapChunks = surfaceFallbackMapChunks.collect(
                surfaceMapChunkCoordinates,
                deliveredSurfaceMapChunks,
                rainPlan,
                fastResult
        );
        List<ChunkPosition> fallbackChunkPositions = surfaceFallbackChunkPlanner.plan(
                metadata,
                fallbackMapChunks
        );
        List<ParsedChunk> fallbackChunks = new ArrayList<>();
        ChunkStreamStats fallbackChunkStats = new ChunkStreamStats(0, 0, 0, 0, 0, 0);
        if (!fallbackChunkPositions.isEmpty()) {
            fallbackChunkStats = reader.forEachChunkByPositionAdaptive(
                    request.savePath(),
                    fallbackChunkPositions,
                    chunkDiagnostics,
                    fallbackChunks::add,
                    progress
            );
        }
        SurfaceScanResult fallbackSurface = fallbackMapChunks.isEmpty()
                ? new SurfaceScanResult(List.of(), 0, 0, 0, 0)
                : surfaceScanner.scan(fallbackChunks, registry, true, progress);
        int healthyFastColumns = rainPlan.targets().stream()
                .filter(target -> !fallbackMapChunks.contains(target.mapChunkCoordinate()))
                .toList()
                .size();
        int chunksScanned = Math.addExact(
                fastChunkStats.parsedChunks(),
                fallbackChunkStats.parsedChunks()
        );
        int columnsScanned = Math.addExact(
                healthyFastColumns,
                fallbackSurface.columnsScanned()
        );
        List<SurfaceBlock> mergedSurfaceBlocks = surfaceFastPathMerger.merge(
                fastResult.blocks(),
                fallbackSurface.blocks(),
                fallbackMapChunks,
                metadata,
                centerWorldX,
                centerWorldZ,
                request.radius()
        );
        SurfaceScanResult surface = new SurfaceScanResult(
                mergedSurfaceBlocks,
                chunksScanned,
                columnsScanned,
                fallbackSurface.emptyColumns(),
                fallbackSurface.liquidUnavailableColumns()
        );
        SurfaceRenderAnalysis analysis;
        if (request.material().isPresent()) {
            List<SurfaceBlock> matchingBlocks = request.material().orElseThrow()
                    .matchingBlocks(surface.blocks());
            analysis = surfaceMaterialAnalyzer.analyzeMatched(
                    request.resourceDisplayName(), matchingBlocks, surface.columnsScanned());
        } else {
            analysis = surfaceObjectAnalyzer.analyze(
                    request.observedResource().orElseThrow(), registry);
        }

        RenderedMap rendered = renderer.render(
                center,
                player,
                home,
                terrain,
                surface.blocks(),
                options,
                progress
        );

        progress.start("Painting surface resource overlay");
        if (analysis instanceof SurfaceMaterialAnalysis materialAnalysis) {
            overlayRenderer.drawMaterial(rendered.image(), center, request.radius(),
                    materialAnalysis, player, home);
        } else if (analysis instanceof SurfaceObjectAnalysis objectAnalysis) {
            overlayRenderer.drawObject(rendered.image(), center, request.radius(),
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
