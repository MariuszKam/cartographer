package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderedMap;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        Objects.requireNonNull(request, "request is required");

        WorldPosition player = reader.readPlayerPosition(request.savePath());
        WorldPosition center = request.center().orElse(player);
        RenderOptions options = new RenderOptions(
                request.radius(),
                request.pixelsPerBlock(),
                request.style(),
                request.layers()
        );

        HomeState home = absoluteHome(request.savePath());
        ReadDiagnostics mapChunkDiagnostics = new ReadDiagnostics();
        List<MapChunk> mapChunks = reader.readMapChunksAround(
                request.savePath(), center, request.radius(), mapChunkDiagnostics
        );

        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        List<ParsedChunk> chunks = reader.readChunksAround(
                request.savePath(), center, request.radius(), chunkDiagnostics
        );
        Map<Integer, cartographer.model.BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        SurfaceScanResult surface = surfaceScanner.scan(chunks, registry, true);
        List<SurfaceBlock> matchingBlocks = request.match().matchingBlocks(surface.blocks());
        SurfaceResourceAnalysis groupedAnalysis = surfaceResourceAnalyzer.analyze(
                matchingBlocks,
                request.match().requiredTokens().getFirst()
        );
        SurfaceResourceAnalysis analysis = new SurfaceResourceAnalysis(
                request.match().displayName(),
                groupedAnalysis.surfaceColumns(),
                groupedAnalysis.matchingBlocks(),
                groupedAnalysis.deposits()
        );

        RenderedMap rendered = renderer.render(
                center,
                player,
                home,
                mapChunks,
                surface.blocks(),
                options
        );

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
                WorldMetadata metadata = metadataReader.read(request.savePath());
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

    private HomeState absoluteHome(java.nio.file.Path savePath) {
        Optional<HomeLocation> displayHome = homeStore.load(savePath);
        if (displayHome.isEmpty()) {
            return HomeState.absent();
        }
        HomeLocation location = displayHome.orElseThrow();
        WorldMetadata metadata = metadataReader.read(savePath);
        WorldPosition absolute = metadata.toAbsolute(
                new DisplayPosition(location.x(), 0.0, location.z())
        );
        return HomeState.present(new HomeLocation(absolute.x(), absolute.z()));
    }

}
