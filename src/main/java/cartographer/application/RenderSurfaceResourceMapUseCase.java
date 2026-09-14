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
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.resource.ObservedSurfaceResource;
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
import cartographer.scanner.SurfaceObjectScanResult;
import cartographer.scanner.SurfaceObjectPlan;
import cartographer.scanner.SurfaceObjectPlanner;
import cartographer.scanner.SurfaceObjectScanner;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.SelectiveChunkStreamStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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
    private final SurfaceResourceAnalyzer surfaceResourceAnalyzer;
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
    private final SurfaceObjectScanner surfaceObjectScanner =
            new SurfaceObjectScanner();
    private final SurfaceObjectPlanner surfaceObjectPlanner =
            new SurfaceObjectPlanner();

    public RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceScanner surfaceScanner,
            SurfaceResourceAnalyzer surfaceResourceAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        this.surfaceScanner = Objects.requireNonNull(surfaceScanner, "surfaceScanner is required");
        this.surfaceResourceAnalyzer = Objects.requireNonNull(
                surfaceResourceAnalyzer,
                "surfaceResourceAnalyzer is required"
        );
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
        SurfaceObjectPlanner.StreamingSession surfaceObjectPlannerSession =
                surfaceObjectPlanner.begin(
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
                        surfaceObjectPlannerSession.accept(mapChunk);
                    }
                },
                progress
        );
        MapTerrainPreparation terrain = terrainBuilder.finish();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        RainHeightSurfacePlan rainPlan = rainPlannerSession.finish();
        SurfaceObjectPlan surfaceObjectPlan = surfaceObjectPlannerSession.finish();
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
        List<SurfaceBlock> matchingBlocks = request.legacyMatch()
                .map(match -> match.matchingBlocks(surface.blocks()))
                .orElseGet(() -> observedBlocks(request.observedResource().orElseThrow(), registry));
        int exposedObsidianCount = 0;
        int looseObsidianCount = 0;
        int surfaceObjectRegistryVariants = 0;
        int surfaceObjectPositionsInspected = 0;
        int surfaceObjectUnavailablePositions = 0;
        int surfaceObjectObservedTargets = 0;
        int surfaceObjectNotObservedTargets = 0;
        SelectiveChunkStreamStats surfaceObjectChunkStats =
                new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0);
        if (request.observedResource().isPresent()) {
            ObservedSurfaceResource resource = request.observedResource().orElseThrow();
            // Discovery already performed the selective scan. Render-time
            // diagnostics must not present observations as scanned positions.
            surfaceObjectRegistryVariants = resource.candidate().blockIds().size();
        }
        if (request.legacyMatch().map(SurfaceResourceMatch::usesSurfaceObjectScan).orElse(false)) {
            SurfaceResourceMatch legacyMatch = request.legacyMatch().orElseThrow();
            int[] wantedBlockIds = legacyMatch.matchingBlockIds(registry);
            surfaceObjectRegistryVariants = wantedBlockIds.length;
            List<ChunkPosition> objectPositions = surfaceObjectPlan.chunkPositions();
            List<ParsedChunk> objectChunks = new ArrayList<>();
            Set<ChunkPosition> availableObjectPositions = new HashSet<>();
            if (wantedBlockIds.length > 0 && !objectPositions.isEmpty()) {
                surfaceObjectChunkStats = reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        request.savePath(),
                        objectPositions,
                        wantedBlockIds,
                        chunkDiagnostics,
                        visit -> {
                            if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                                objectChunks.add(visit.chunk());
                                availableObjectPositions.add(visit.position());
                            } else if (visit.status()
                                    == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
                                availableObjectPositions.add(visit.position());
                            }
                        },
                        progress
                );
            }
            progress.start("Analyzing surface resources");
            SurfaceObjectScanResult objectResult = surfaceObjectScanner.scan(
                    surfaceObjectPlan,
                    registry,
                    java.util.Arrays.stream(wantedBlockIds)
                            .boxed()
                            .collect(java.util.stream.Collectors.toSet()),
                    objectChunks,
                    availableObjectPositions
            );
            surfaceObjectPositionsInspected = objectResult.positionsInspected();
            surfaceObjectUnavailablePositions = objectResult.unavailablePositions();
            surfaceObjectObservedTargets = objectResult.observedTargets();
            surfaceObjectNotObservedTargets = objectResult.notObservedTargets();
            List<SurfaceBlock> exposedObsidian = surface.blocks().stream()
                    .filter(legacyMatch::matchesExposedSurfaceObsidian)
                    .toList();
            List<SurfaceBlock> looseObsidian = new ArrayList<>(
                    legacyMatch.matchingBlocks(fallbackSurface.blocks())
            );
            looseObsidian.addAll(objectResult.blocks());
            matchingBlocks = distinctSurfaceBlocks(exposedObsidian, looseObsidian);
            exposedObsidianCount = (int) matchingBlocks.stream()
                    .filter(legacyMatch::matchesExposedSurfaceObsidian)
                    .count();
            looseObsidianCount = matchingBlocks.size() - exposedObsidianCount;
        }
        SurfaceResourceAnalysis analysis = surfaceResourceAnalyzer.analyzeMatched(
                request.resourceDisplayName(),
                matchingBlocks,
                surface.columnsScanned()
        );

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
        overlayRenderer.draw(
                rendered.image(),
                center,
                request.radius(),
                analysis,
                player,
                home
        );

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
                userMarkersDrawn,
                exposedObsidianCount,
                looseObsidianCount,
                surfaceObjectRegistryVariants,
                surfaceObjectPositionsInspected,
                surfaceObjectUnavailablePositions,
                surfaceObjectObservedTargets,
                surfaceObjectNotObservedTargets,
                surfaceObjectChunkStats,
                request.observedResource().isPresent()
                        ? SurfaceObjectDataSource.DISCOVERY_RESULT
                        : request.legacyMatch().map(SurfaceResourceMatch::usesSurfaceObjectScan).orElse(false)
                                ? SurfaceObjectDataSource.LEGACY_SCAN
                                : SurfaceObjectDataSource.NONE
        );
    }

    private List<SurfaceBlock> observedBlocks(
            ObservedSurfaceResource resource,
            Map<Integer, BlockInfo> registry
    ) {
        return resource.observations().stream().map(observation -> {
            BlockInfo block = registry.get(observation.blockId());
            if (block == null) {
                throw new IllegalStateException(
                        "Discovery observation references missing block ID: "
                                + observation.blockId()
                );
            }
            return new SurfaceBlock(
                    observation.worldX(),
                    observation.worldY(),
                    observation.worldZ(),
                    block
            );
        }).toList();
    }

    private List<SurfaceBlock> distinctSurfaceBlocks(
            List<SurfaceBlock> exposedObsidian,
            List<SurfaceBlock> looseObsidian
    ) {
        Map<String, SurfaceBlock> distinct = new LinkedHashMap<>();
        for (SurfaceBlock block : exposedObsidian) {
            distinct.put(surfaceBlockKey(block), block);
        }
        for (SurfaceBlock block : looseObsidian) {
            distinct.putIfAbsent(surfaceBlockKey(block), block);
        }
        return List.copyOf(distinct.values());
    }

    private String surfaceBlockKey(SurfaceBlock block) {
        return block.worldX() + ":"
                + block.y() + ":"
                + block.worldZ() + ":"
                + block.blockInfo().code().toLowerCase(java.util.Locale.ROOT);
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
