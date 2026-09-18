package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.ServerMapRegion;
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
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.perf.RenderDataCacheStore;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.MultiActualBlockMapScanner;
import cartographer.scanner.SurfaceMapScanResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RenderActualOreMapUseCase {
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final PrepareMapDataUseCase mapDataUseCase;
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
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader
                )
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
            RenderDataCacheStore renderDataCacheStore
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
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader
                ),
                renderDataCacheStore
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
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader
                )
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
                oreChunkPositionPlanner,
                new SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader
                )
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
            OreChunkPositionPlanner oreChunkPositionPlanner,
            SaveSessionFactory sessionFactory
    ) {
        this(reader, metadataReader, homeStore, markerStore, renderer, userMarkerRenderer,
                actualBlockMapScanner, actualOreOverlayPainter, multiActualBlockMapScanner,
                oreChunkPositionPlanner, sessionFactory, Optional.empty());
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
            OreChunkPositionPlanner oreChunkPositionPlanner,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(reader, metadataReader, homeStore, markerStore, renderer, userMarkerRenderer,
                actualBlockMapScanner, actualOreOverlayPainter, multiActualBlockMapScanner,
                oreChunkPositionPlanner, sessionFactory,
                Optional.of(Objects.requireNonNull(renderDataCacheStore, "render data cache store is required")));
    }

    private RenderActualOreMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter,
            MultiActualBlockMapScanner multiActualBlockMapScanner,
            OreChunkPositionPlanner oreChunkPositionPlanner,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        Objects.requireNonNull(metadataReader, "metadataReader is required");
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

        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            return execute(saveSession, request, progress);
        }
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

        boolean surfaceDataRequired =
                request.layers().contains(RenderLayer.SURFACE)
                        || request.layers().contains(RenderLayer.SOIL_FERTILITY);
        PreparedMapData prepared = mapDataUseCase.execute(
                saveSession,
                new PrepareMapDataRequest(
                        request.savePath(),
                        request.radius(),
                        request.pixelsPerBlock(),
                        request.style(),
                        request.layers(),
                        request.center(),
                        surfaceDataRequired
                ),
                progress
        );
        WorldMetadata metadata = prepared.metadata();
        WorldPosition player = prepared.player();
        WorldPosition center = prepared.center();
        RenderOptions options = prepared.options();
        HomeState home = absoluteHome(request.savePath(), metadata);
        SurfaceMapScanResult compactSurface = prepared.surface();
        ReadDiagnostics mapChunkDiagnostics = prepared.mapChunkDiagnostics();
        ReadDiagnostics chunkDiagnostics = prepared.chunkDiagnostics();

        RenderedMap rendered = renderer.render(
                center, player, home, prepared.terrain(), compactSurface.map(),
                prepared.registry(), options, progress
        );

        ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
        List<ServerMapRegion> mapRegions = hasMapRegionOverlay(options)
                ? readMapRegionsWithProgress(
                        saveSession, mapRegionDiagnostics, progress
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
                saveSession, request, rendered, center, metadata,
                prepared.registry(), actualOreDiagnostics, progress
        );

        if ((hasMapRegionOverlay(options) || !actualOreOverlays.isEmpty())
                && options.layers().contains(RenderLayer.MARKERS)) {
            systemMarkerOverlayRenderer.draw(
                    rendered.image(), center, player, home, request.radius()
            );
        }

        MapDecorationState decorations =
                decorationState(request.savePath(), home, options);
        List<cartographer.marker.UserMarker> userMarkers =
                decorations.userMarkers();
        int userMarkersDrawn = 0;
        if (options.layers().contains(RenderLayer.MARKERS)
                && !userMarkers.isEmpty()) {
            userMarkersDrawn = userMarkerRenderer.draw(
                    rendered.image(),
                    center,
                    request.radius(),
                    userMarkers,
                    metadata
            );
        }

        return new RenderActualOreMapResult(
                rendered.image(), rendered.geometry(), rendered.report(),
                compactSurface,
                environmentOverlay, geologyOverlay,
                actualOreOverlays.isEmpty()
                        ? Optional.empty()
                        : Optional.of(actualOreOverlays.getFirst().map()),
                mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn, actualOreOverlays,
                prepared.renderDataCacheReport(),
                Optional.of(prepared),
                Optional.of(decorations)
        );
    }

    private List<ServerMapRegion> readMapRegionsWithProgress(
            SaveSession saveSession,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start("Reading map regions");
        return reader.readMapRegions(saveSession, diagnostics, progress);
    }

    private List<ActualOreOverlayResult> drawActualOreOverlays(
            SaveSession saveSession,
            RenderActualOreMapRequest request,
            RenderedMap rendered,
            WorldPosition center,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        List<ActualOreOverlaySpec> specs = request.oreOverlays();
        if (specs.isEmpty()) {
            return List.of();
        }
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
                    saveSession,
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

    private MapDecorationState decorationState(
            java.nio.file.Path savePath,
            HomeState home,
            RenderOptions options
    ) {
        try {
            return new MapDecorationState(
                    home,
                    markerStore.load(savePath),
                    true
            );
        } catch (RuntimeException exception) {
            if (options.layers().contains(RenderLayer.MARKERS)) {
                throw exception;
            }
            return new MapDecorationState(home, List.of(), false);
        }
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
