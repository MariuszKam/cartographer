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
import cartographer.snapshot.SnapshotMapRegionReader;
import cartographer.snapshot.SnapshotResourceReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.MultiActualBlockMapScanner;

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
    private final Optional<SnapshotMapRegionReader> snapshotMapRegionReader;
    private final Optional<SnapshotResourceReader> snapshotResourceReader;

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
        this.snapshotMapRegionReader = cacheOption.map(
                SnapshotMapRegionReader::new
        );
        this.snapshotResourceReader = cacheOption.map(
                SnapshotResourceReader::new
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
                snapshotMapRegionState(
                        request.savePath(),
                        options,
                        progress
                );
        if (mapRegionState.isEmpty()) {
            return Optional.empty();
        }

        Optional<List<ActualOreOverlayResult>> oreOverlays =
                snapshotOreOverlays(
                        request,
                        prepared,
                        progress
                );
        if (oreOverlays.isEmpty()) {
            return Optional.empty();
        }

        HomeState home = absoluteHome(request.savePath(), metadata);
        MapDecorationState decorations =
                decorationState(request.savePath(), home, options);
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

    private Optional<MapRegionOverlayState> snapshotMapRegionState(
            Path savePath,
            RenderOptions options,
            ProgressReporter progress
    ) {
        boolean environmentRequested =
                options.layers().contains(RenderLayer.ENVIRONMENT);
        boolean geologyRequested =
                options.layers().contains(RenderLayer.GEOLOGY);
        if (!environmentRequested && !geologyRequested) {
            return Optional.of(new MapRegionOverlayState(
                    Optional.empty(),
                    Optional.empty()
            ));
        }
        if (snapshotMapRegionReader.isEmpty()) {
            return Optional.empty();
        }

        progress.start("Reading map-region overlays from world snapshot");
        Optional<SnapshotMapRegionReader.Result> snapshot =
                snapshotMapRegionReader.orElseThrow().read(savePath);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        SnapshotMapRegionReader.Result result = snapshot.orElseThrow();
        return Optional.of(new MapRegionOverlayState(
                environmentRequested
                        ? Optional.of(result.environmentProfiles())
                        : Optional.empty(),
                geologyRequested
                        ? Optional.of(result.geologySummaries())
                        : Optional.empty()
        ));
    }

    private Optional<List<ActualOreOverlayResult>> snapshotOreOverlays(
            RenderActualOreMapRequest request,
            PreparedMapData prepared,
            ProgressReporter progress
    ) {
        List<ActualOreOverlaySpec> specs = request.oreOverlays();
        if (specs.isEmpty()) {
            return Optional.of(List.of());
        }
        if (snapshotResourceReader.isEmpty()) {
            return Optional.empty();
        }

        List<ActualBlockMatchSpec> matches = specs.stream()
                .map(spec -> new ActualBlockMatchSpec(
                        spec.match(),
                        spec.matchMode()
                ))
                .toList();
        int centerX = checkedRound(prepared.center().x());
        int centerZ = checkedRound(prepared.center().z());

        progress.start("Reading actual ore from world snapshot");
        Optional<List<ActualBlockMap>> maps =
                snapshotResourceReader.orElseThrow().readMaps(
                        request.savePath(),
                        prepared.metadata(),
                        prepared.registry(),
                        centerX,
                        centerZ,
                        request.radius(),
                        matches,
                        request.yFilter()
                );
        if (maps.isEmpty() || maps.orElseThrow().size() != specs.size()) {
            return Optional.empty();
        }

        List<ActualOreOverlayResult> result =
                new java.util.ArrayList<>(specs.size());
        for (int index = 0; index < specs.size(); index++) {
            result.add(new ActualOreOverlayResult(
                    specs.get(index),
                    maps.orElseThrow().get(index)
            ));
        }
        return Optional.of(List.copyOf(result));
    }

    private int checkedRound(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "world center must be finite"
            );
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world center is outside the supported block range"
            );
        }
        return (int) rounded;
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
        HomeState home = absoluteHome(request.savePath(), metadata);
        MapDecorationState decorations =
                decorationState(request.savePath(), home, options);
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
                resolveMapRegionOverlays(
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
        List<ActualOreOverlayResult> actualOreOverlays = drawActualOreOverlays(
                saveSession,
                request,
                rendered,
                center,
                metadata,
                prepared.registry(),
                actualOreDiagnostics,
                progress
        );

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

    private MapRegionOverlayState resolveMapRegionOverlays(
            SaveSession saveSession,
            RenderOptions options,
            Optional<MapRegionOverlayState> retainedState,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        boolean environmentRequested =
                options.layers().contains(RenderLayer.ENVIRONMENT);
        boolean geologyRequested =
                options.layers().contains(RenderLayer.GEOLOGY);
        Optional<List<EnvironmentProfile>> retainedEnvironment =
                retainedState.flatMap(MapRegionOverlayState::environmentProfiles);
        Optional<List<GeologicProvinceSummary>> retainedGeology =
                retainedState.flatMap(MapRegionOverlayState::geologySummaries);
        boolean needEnvironment =
                environmentRequested && retainedEnvironment.isEmpty();
        boolean needGeology =
                geologyRequested && retainedGeology.isEmpty();

        Optional<SnapshotMapRegionReader.Result> snapshot =
                Optional.empty();
        if ((needEnvironment || needGeology)
                && snapshotMapRegionReader.isPresent()) {
            progress.start("Reading map-region overlays from world snapshot");
            snapshot = snapshotMapRegionReader.orElseThrow().read(
                    saveSession.savePath()
            );
        }
        Optional<List<EnvironmentProfile>> snapshotEnvironment =
                snapshot.map(
                        SnapshotMapRegionReader.Result::environmentProfiles
                );
        Optional<List<GeologicProvinceSummary>> snapshotGeology =
                snapshot.map(
                        SnapshotMapRegionReader.Result::geologySummaries
                );

        boolean sourceEnvironment =
                needEnvironment && snapshotEnvironment.isEmpty();
        boolean sourceGeology =
                needGeology && snapshotGeology.isEmpty();
        List<ServerMapRegion> regions =
                sourceEnvironment || sourceGeology
                        ? readMapRegionsWithProgress(
                        saveSession,
                        diagnostics,
                        progress
                )
                        : List.of();

        Optional<List<EnvironmentProfile>> environmentProfiles =
                retainedEnvironment.isPresent()
                        ? retainedEnvironment
                        : !environmentRequested
                        ? Optional.empty()
                        : snapshotEnvironment.isPresent()
                        ? snapshotEnvironment
                        : Optional.of(
                        regions.stream()
                                .map(environmentInterpreter::interpret)
                                .toList()
                );
        Optional<List<GeologicProvinceSummary>> geologySummaries =
                retainedGeology.isPresent()
                        ? retainedGeology
                        : !geologyRequested
                        ? Optional.empty()
                        : snapshotGeology.isPresent()
                        ? snapshotGeology
                        : Optional.of(
                        regions.stream()
                                .map(geologicProvinceInterpreter::summarize)
                                .flatMap(Optional::stream)
                                .toList()
                );

        return new MapRegionOverlayState(
                environmentProfiles,
                geologySummaries
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
        Optional<List<ActualBlockMap>> snapshotMaps =
                Optional.empty();
        if (snapshotResourceReader.isPresent()) {
            progress.start("Reading actual ore from world snapshot");
            snapshotMaps = snapshotResourceReader.orElseThrow().readMaps(
                    request.savePath(),
                    metadata,
                    registry,
                    centerX,
                    centerZ,
                    request.radius(),
                    matches,
                    request.yFilter()
            );
        }

        List<ActualBlockMap> maps;
        if (snapshotMaps.isPresent()) {
            maps = snapshotMaps.orElseThrow();
            progress.done("Actual ore loaded from world snapshot");
        } else {
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
                List<cartographer.model.ChunkPosition> positions =
                        oreChunkPositionPlanner.plan(
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
