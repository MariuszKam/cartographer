package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.model.BlockInfo;
import cartographer.navigation.HomeStore;
import cartographer.perf.RenderDataCacheStore;
import cartographer.render.MapRenderer;
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
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceMapScanResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RenderSurfaceResourceMapUseCase {

    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final SurfaceMaterialAnalyzer surfaceMaterialAnalyzer;
    private final SurfaceObjectAnalyzer surfaceObjectAnalyzer;
    private final SurfaceResourceOverlayRenderer overlayRenderer;
    private final PrepareMapDataUseCase mapDataUseCase;

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
                overlayRenderer,
                Optional.empty()
        );
    }

    public RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceMaterialAnalyzer surfaceMaterialAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer,
            RenderDataCacheStore renderDataCacheStore
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
                overlayRenderer,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
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
        this(
                reader,
                metadataReader,
                sessionFactory,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                surfaceMaterialAnalyzer,
                overlayRenderer,
                Optional.empty()
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
            SurfaceResourceOverlayRenderer overlayRenderer,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                metadataReader,
                sessionFactory,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                surfaceMaterialAnalyzer,
                overlayRenderer,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    private RenderSurfaceResourceMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionFactory sessionFactory,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            SurfaceMaterialAnalyzer surfaceMaterialAnalyzer,
            SurfaceResourceOverlayRenderer overlayRenderer,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(
                userMarkerRenderer,
                "userMarkerRenderer is required"
        );
        this.surfaceMaterialAnalyzer = Objects.requireNonNull(
                surfaceMaterialAnalyzer,
                "surfaceMaterialAnalyzer is required"
        );
        this.surfaceObjectAnalyzer = new SurfaceObjectAnalyzer();
        this.overlayRenderer = Objects.requireNonNull(
                overlayRenderer,
                "overlayRenderer is required"
        );
        this.mapDataUseCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory,
                Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache option is required"
                )
        );
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
        session.requireSameSave(request.savePath());

        PreparedMapData prepared = mapDataUseCase.execute(
                session,
                new PrepareMapDataRequest(
                        request.savePath(),
                        request.radius(),
                        request.pixelsPerBlock(),
                        request.style(),
                        request.layers(),
                        request.center(),
                        true
                ),
                progress
        );
        WorldMetadata metadata = prepared.metadata();
        RenderOptions options = prepared.options();
        HomeState home = absoluteHome(request.savePath(), metadata);
        MapDecorationState decorations =
                decorationState(request.savePath(), home, options);
        return renderPrepared(
                request,
                prepared,
                decorations,
                prepared.mapChunkDiagnostics(),
                prepared.chunkDiagnostics(),
                prepared.renderDataCacheReport(),
                progress
        );
    }

    public RenderSurfaceResourceMapResult executeRetained(
            RenderSurfaceResourceMapRequest request,
            java.nio.file.Path retainedSavePath,
            PreparedMapData prepared,
            MapDecorationState decorations,
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
        Objects.requireNonNull(progress, "progress is required");
        requireRetainedCompatibility(request, prepared);
        return renderPrepared(
                request,
                prepared,
                decorations,
                new ReadDiagnostics(),
                new ReadDiagnostics(),
                RenderDataCacheReport.disabled(
                        "retained PreparedMapData reused; no save/cache preparation read"
                ),
                progress
        );
    }

    private RenderSurfaceResourceMapResult renderPrepared(
            RenderSurfaceResourceMapRequest request,
            PreparedMapData prepared,
            MapDecorationState decorations,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            RenderDataCacheReport cacheReport,
            ProgressReporter progress
    ) {
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
        Map<Integer, BlockInfo> registry = prepared.registry();
        SurfaceMapScanResult surface = prepared.surface();

        SurfaceRenderAnalysis analysis;
        if (request.material().isPresent()) {
            analysis = surfaceMaterialAnalyzer.analyze(
                    surface,
                    request.material().orElseThrow(),
                    request.resourceDisplayName()
            );
        } else {
            analysis = new SurfaceObjectSelectionAnalysis(
                    request.observedResources().stream()
                            .map(resource -> surfaceObjectAnalyzer.analyze(resource, registry))
                            .toList()
            );
        }

        RenderedMap rendered = renderer.render(
                center,
                player,
                decorations.home(),
                prepared.terrain(),
                surface.map(),
                registry,
                options,
                progress
        );

        progress.start("Painting surface resource overlay");
        if (analysis instanceof SurfaceMaterialAnalysis materialAnalysis) {
            overlayRenderer.drawMaterial(
                    rendered.image(),
                    center,
                    request.radius(),
                    materialAnalysis,
                    player,
                    decorations.home(),
                    options.layers().contains(RenderLayer.MARKERS)
            );
        } else if (analysis instanceof SurfaceObjectSelectionAnalysis objectAnalysis) {
            overlayRenderer.drawObjects(
                    rendered.image(),
                    center,
                    request.radius(),
                    objectAnalysis,
                    player,
                    decorations.home(),
                    options.layers().contains(RenderLayer.MARKERS)
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

        return new RenderSurfaceResourceMapResult(
                rendered.image(),
                rendered.geometry(),
                analysis,
                surface,
                rendered.report(),
                mapChunkDiagnostics,
                chunkDiagnostics,
                userMarkersDrawn,
                cacheReport,
                Optional.of(prepared),
                Optional.of(decorations)
        );
    }

    private void requireRetainedCompatibility(
            RenderSurfaceResourceMapRequest request,
            PreparedMapData prepared
    ) {
        RenderOptions options = prepared.options();
        if (request.radius() != options.radiusBlocks()
                || request.pixelsPerBlock() != options.pixelsPerBlock()
                || request.style() != options.style()) {
            throw new IllegalArgumentException(
                    "retained PreparedMapData does not match Surface render geometry/style"
            );
        }
        if (request.center().isPresent()
                && !request.center().orElseThrow().equals(prepared.center())) {
            throw new IllegalArgumentException(
                    "retained PreparedMapData does not match Surface render center"
            );
        }
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
        WorldPosition absolute = metadata.toAbsolute(
                new DisplayPosition(location.x(), 0.0, location.z())
        );
        return HomeState.present(new HomeLocation(absolute.x(), absolute.z()));
    }

}
