package cartographer.application;

import cartographer.render.ActualOreOverlayResult;
import cartographer.spatial.OreChunkPositionPlanner;
import cartographer.progress.ProgressReporter;
import cartographer.marker.MarkerStore;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.EnvironmentOverlayRenderer;
import cartographer.render.GeologyOverlayRenderer;
import cartographer.render.MapRenderer;
import cartographer.render.OverlayRenderReport;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderedMap;
import cartographer.render.SystemMarkerOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.cache.RenderDataCacheStore;
import cartographer.scanner.MultiActualBlockMapScanner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RenderActualOreMapUseCase {
    private final SaveSessionFactory sessionFactory;
    private final PrepareMapDataUseCase mapDataUseCase;
    private final MapRegionOverlayResolver mapRegionOverlayResolver;
    private final ActualOreOverlayResolver actualOreOverlayResolver;
    private final MapDecorationResolver decorationResolver;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final ActualOreOverlayPainter actualOreOverlayPainter;
    private final EnvironmentOverlayRenderer environmentOverlayRenderer = new EnvironmentOverlayRenderer();
    private final GeologyOverlayRenderer geologyOverlayRenderer = new GeologyOverlayRenderer();
    private final SystemMarkerOverlayRenderer systemMarkerOverlayRenderer = new SystemMarkerOverlayRenderer();

    public RenderActualOreMapUseCase(
            VcdbsReader reader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualOreOverlayPainter actualOreOverlayPainter,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                actualOreOverlayPainter,
                new MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                sessionFactory,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }


    RenderActualOreMapUseCase(
            VcdbsReader reader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualOreOverlayPainter actualOreOverlayPainter,
            MultiActualBlockMapScanner multiActualBlockMapScanner,
            OreChunkPositionPlanner oreChunkPositionPlanner,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory is required");
        Optional<RenderDataCacheStore> cacheOption = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        );
        this.mapDataUseCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory,
                cacheOption
        );
        this.mapRegionOverlayResolver = new MapRegionOverlayResolver(
                reader,
                cacheOption
        );
        this.actualOreOverlayResolver = new ActualOreOverlayResolver(
                reader,
                multiActualBlockMapScanner,
                oreChunkPositionPlanner,
                cacheOption
        );
        this.decorationResolver = new MapDecorationResolver(
                homeStore,
                markerStore
        );
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        this.actualOreOverlayPainter = Objects.requireNonNull(actualOreOverlayPainter, "actualOreOverlayPainter is required");
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

        Optional<RenderActualOreMapResult> snapshot =
                executeSnapshotMap(request, progress);
        if (snapshot.isPresent()) {
            return snapshot.orElseThrow();
        }

        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            return execute(saveSession, request, progress);
        }
    }

    private Optional<RenderActualOreMapResult> executeSnapshotMap(
            RenderActualOreMapRequest request,
            ProgressReporter progress
    ) {
        SurfaceDataRequirement surfaceDataRequirement =
                request.layers().contains(RenderLayer.SOIL_FERTILITY)
                        ? SurfaceDataRequirement.ANALYSIS
                        : request.layers().contains(RenderLayer.SURFACE)
                        ? SurfaceDataRequirement.RENDER
                        : SurfaceDataRequirement.NONE;
        Optional<PreparedMapData> preparedOptional =
                mapDataUseCase.executeSnapshot(
                        new PrepareMapDataRequest(
                                request.savePath(),
                                request.radius(),
                                request.pixelsPerBlock(),
                                request.style(),
                                request.layers(),
                                request.center(),
                                surfaceDataRequirement
                        ),
                        progress
                );
        if (preparedOptional.isEmpty()) {
            return Optional.empty();
        }

        PreparedMapData prepared = preparedOptional.orElseThrow();
        WorldMetadata metadata = prepared.metadata();
        WorldPosition player = prepared.player();
        WorldPosition center = prepared.center();
        RenderOptions options = prepared.options();

        Optional<MapRegionOverlayState> mapRegionState =
                mapRegionOverlayResolver.snapshot(
                        request.savePath(),
                        options,
                        progress
                );
        if (mapRegionState.isEmpty()) {
            return Optional.empty();
        }

        Optional<List<ActualOreOverlayResult>> oreOverlays =
                actualOreOverlayResolver.snapshot(
                        request,
                        prepared,
                        progress
                );
        if (oreOverlays.isEmpty()) {
            return Optional.empty();
        }

        MapDecorationState decorations = decorationResolver.resolve(
                request.savePath(),
                metadata,
                options
        );
        if (options.layers().contains(RenderLayer.MARKERS)
                && !decorations.userMarkersAvailable()) {
            throw new IllegalStateException(
                    "marker state is unavailable; full render cannot proceed"
            );
        }

        PreparedSurfaceData preparedSurface = prepared.surface();
        cartographer.scanner.SurfaceMap exactSoil =
                options.layers().contains(RenderLayer.SOIL_FERTILITY)
                        ? preparedSurface.requireAnalysis().map()
                        : null;
        progress.start("Rendering map from world snapshot");
        RenderedMap rendered = renderer.render(
                center,
                player,
                decorations.home(),
                prepared.terrain(),
                preparedSurface.renderData(),
                exactSoil,
                prepared.registry(),
                options,
                progress
        );

        if (hasMapRegionOverlay(options)) {
            progress.start("Painting snapshot map-region overlays");
        }
        OverlayRenderReport environmentOverlay = drawEnvironmentOverlay(
                rendered,
                center,
                request.radius(),
                options,
                mapRegionState.orElseThrow()
        );
        OverlayRenderReport geologyOverlay = drawGeologyOverlay(
                rendered,
                center,
                request.radius(),
                options,
                mapRegionState.orElseThrow()
        );

        List<ActualOreOverlayResult> actualOreOverlays =
                oreOverlays.orElseThrow();
        if (!actualOreOverlays.isEmpty()) {
            progress.start("Painting actual ore from world snapshot");
            actualOreOverlayPainter.paint(
                    rendered.image(),
                    actualOreOverlays,
                    center,
                    request.radius()
            );
        }

        if ((hasMapRegionOverlay(options) || !actualOreOverlays.isEmpty())
                && options.layers().contains(RenderLayer.MARKERS)) {
            systemMarkerOverlayRenderer.draw(
                    rendered.image(),
                    center,
                    player,
                    decorations.home(),
                    request.radius()
            );
        }

        int userMarkersDrawn = 0;
        if (options.layers().contains(RenderLayer.MARKERS)
                && !decorations.userMarkers().isEmpty()) {
            userMarkersDrawn = userMarkerRenderer.draw(
                    rendered.image(),
                    center,
                    request.radius(),
                    decorations.userMarkers(),
                    metadata
            );
        }

        progress.done("Rendered map from world snapshot");
        return Optional.of(new RenderActualOreMapResult(
                rendered.image(),
                rendered.geometry(),
                rendered.report(),
                preparedSurface.diagnostics(),
                environmentOverlay,
                geologyOverlay,
                actualOreOverlays.isEmpty()
                        ? Optional.empty()
                        : Optional.of(actualOreOverlays.getFirst().map()),
                prepared.mapChunkDiagnostics(),
                prepared.chunkDiagnostics(),
                new ReadDiagnostics(),
                new ReadDiagnostics(),
                userMarkersDrawn,
                actualOreOverlays,
                prepared.renderDataCacheReport(),
                Optional.of(prepared),
                Optional.of(decorations),
                Optional.of(mapRegionState.orElseThrow())
        ));
    }

    public RenderActualOreMapResult execute(
            SaveSession saveSession,
            RenderActualOreMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(saveSession, "saveSession is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        saveSession.requireSameSave(request.savePath());

        SurfaceDataRequirement surfaceDataRequirement =
                request.layers().contains(RenderLayer.SOIL_FERTILITY)
                        ? SurfaceDataRequirement.ANALYSIS
                        : request.layers().contains(RenderLayer.SURFACE)
                        ? SurfaceDataRequirement.RENDER
                        : SurfaceDataRequirement.NONE;
        PreparedMapData prepared = mapDataUseCase.execute(
                saveSession,
                new PrepareMapDataRequest(
                        request.savePath(),
                        request.radius(),
                        request.pixelsPerBlock(),
                        request.style(),
                        request.layers(),
                        request.center(),
                        surfaceDataRequirement
                ),
                progress
        );
        WorldMetadata metadata = prepared.metadata();
        RenderOptions options = prepared.options();
        MapDecorationState decorations = decorationResolver.resolve(
                request.savePath(),
                metadata,
                options
        );
        return renderPrepared(
                saveSession,
                request,
                prepared,
                decorations,
                Optional.empty(),
                prepared.mapChunkDiagnostics(),
                prepared.chunkDiagnostics(),
                prepared.renderDataCacheReport(),
                progress
        );
    }

    public RenderActualOreMapResult executeRetained(
            RenderActualOreMapRequest request,
            java.nio.file.Path retainedSavePath,
            PreparedMapData prepared,
            MapDecorationState decorations,
            Optional<MapRegionOverlayState> retainedMapRegionState,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(retainedSavePath, "retainedSavePath is required");
        if (!request.savePath().toAbsolutePath().normalize().equals(
                retainedSavePath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "retained PreparedMapData belongs to a different save"
            );
        }
        Objects.requireNonNull(prepared, "prepared is required");
        Objects.requireNonNull(decorations, "decorations are required");
        Objects.requireNonNull(retainedMapRegionState, "retainedMapRegionState is required");
        Objects.requireNonNull(progress, "progress is required");
        requireRetainedCompatibility(request, prepared);
        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            return renderPrepared(
                    saveSession,
                    request,
                    prepared,
                    decorations,
                    retainedMapRegionState,
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    RenderDataCacheReport.disabled(
                            "retained PreparedMapData reused; source session limited to requested ore/map-region work"
                    ),
                    progress
            );
        }
    }

    private RenderActualOreMapResult renderPrepared(
            SaveSession saveSession,
            RenderActualOreMapRequest request,
            PreparedMapData prepared,
            MapDecorationState decorations,
            Optional<MapRegionOverlayState> retainedMapRegionState,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            RenderDataCacheReport cacheReport,
            ProgressReporter progress
    ) {
        saveSession.requireSameSave(request.savePath());
        WorldMetadata metadata = prepared.metadata();
        WorldPosition player = prepared.player();
        WorldPosition center = prepared.center();
        RenderOptions options = new RenderOptions(
                request.radius(),
                request.pixelsPerBlock(),
                request.style(),
                request.layers()
        );
        if (options.layers().contains(RenderLayer.MARKERS)
                && !decorations.userMarkersAvailable()) {
            throw new IllegalStateException(
                    "retained marker state is unavailable; full render is required"
            );
        }
        PreparedSurfaceData preparedSurface = prepared.surface();
        cartographer.scanner.SurfaceMap exactSoil =
                options.layers().contains(RenderLayer.SOIL_FERTILITY)
                        ? preparedSurface.requireAnalysis().map()
                        : null;

        RenderedMap rendered = renderer.render(
                center,
                player,
                decorations.home(),
                prepared.terrain(),
                preparedSurface.renderData(),
                exactSoil,
                prepared.registry(),
                options,
                progress
        );

        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        MapRegionOverlayState mapRegionOverlayState =
                mapRegionOverlayResolver.resolve(
                        saveSession,
                        options,
                        retainedMapRegionState,
                        mapRegionDiagnostics,
                        progress
                );
        if (hasMapRegionOverlay(options)) {
            progress.start("Painting map-region overlays");
        }
        OverlayRenderReport environmentOverlay = drawEnvironmentOverlay(
                rendered,
                center,
                request.radius(),
                options,
                mapRegionOverlayState
        );
        OverlayRenderReport geologyOverlay = drawGeologyOverlay(
                rendered,
                center,
                request.radius(),
                options,
                mapRegionOverlayState
        );

        ReadDiagnostics actualOreDiagnostics = new ReadDiagnostics();
        List<ActualOreOverlayResult> actualOreOverlays =
                actualOreOverlayResolver.resolve(
                        saveSession,
                        request,
                        center,
                        metadata,
                        prepared.registry(),
                        actualOreDiagnostics,
                        progress
                );
        if (!actualOreOverlays.isEmpty()) {
            actualOreOverlayPainter.paint(
                    rendered.image(),
                    actualOreOverlays,
                    center,
                    request.radius()
            );
        }

        if ((hasMapRegionOverlay(options) || !actualOreOverlays.isEmpty())
                && options.layers().contains(RenderLayer.MARKERS)) {
            systemMarkerOverlayRenderer.draw(
                    rendered.image(),
                    center,
                    player,
                    decorations.home(),
                    request.radius()
            );
        }

        int userMarkersDrawn = 0;
        if (options.layers().contains(RenderLayer.MARKERS)
                && !decorations.userMarkers().isEmpty()) {
            userMarkersDrawn = userMarkerRenderer.draw(
                    rendered.image(),
                    center,
                    request.radius(),
                    decorations.userMarkers(),
                    metadata
            );
        }

        return new RenderActualOreMapResult(
                rendered.image(),
                rendered.geometry(),
                rendered.report(),
                preparedSurface.diagnostics(),
                environmentOverlay,
                geologyOverlay,
                actualOreOverlays.isEmpty()
                        ? Optional.empty()
                        : Optional.of(actualOreOverlays.getFirst().map()),
                mapChunkDiagnostics,
                chunkDiagnostics,
                mapRegionDiagnostics,
                actualOreDiagnostics,
                userMarkersDrawn,
                actualOreOverlays,
                cacheReport,
                Optional.of(prepared),
                Optional.of(decorations),
                Optional.of(mapRegionOverlayState)
        );
    }

    private void requireRetainedCompatibility(
            RenderActualOreMapRequest request,
            PreparedMapData prepared
    ) {
        RenderOptions options = prepared.options();
        if (request.radius() != options.radiusBlocks()
                || request.pixelsPerBlock() != options.pixelsPerBlock()
                || request.style() != options.style()) {
            throw new IllegalArgumentException(
                    "retained PreparedMapData does not match Ore render geometry/style"
            );
        }
        if (request.center().isPresent()
                && !request.center().orElseThrow().equals(prepared.center())) {
            throw new IllegalArgumentException(
                    "retained PreparedMapData does not match Ore render center"
            );
        }
    }

    private OverlayRenderReport drawEnvironmentOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            MapRegionOverlayState state
    ) {
        if (!options.layers().contains(RenderLayer.ENVIRONMENT)) {
            return OverlayRenderReport.none();
        }
        return state.environmentProfiles()
                .map(profiles -> environmentOverlayRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        profiles
                ))
                .orElseGet(OverlayRenderReport::none);
    }

    private OverlayRenderReport drawGeologyOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            MapRegionOverlayState state
    ) {
        if (!options.layers().contains(RenderLayer.GEOLOGY)) {
            return OverlayRenderReport.none();
        }
        return state.geologySummaries()
                .map(summaries -> geologyOverlayRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        summaries
                ))
                .orElseGet(OverlayRenderReport::none);
    }

    private boolean hasMapRegionOverlay(RenderOptions options) {
        return options.layers().contains(RenderLayer.ENVIRONMENT)
                || options.layers().contains(RenderLayer.GEOLOGY);
    }

}
