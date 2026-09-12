package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
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
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RenderActualOreMapUseCase {

    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final MapRenderer renderer;
    private final UserMarkerRenderer userMarkerRenderer;
    private final ActualBlockMapScanner actualBlockMapScanner;
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
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader is required");
        this.homeStore = Objects.requireNonNull(homeStore, "homeStore is required");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(userMarkerRenderer, "userMarkerRenderer is required");
        this.actualBlockMapScanner = Objects.requireNonNull(actualBlockMapScanner, "actualBlockMapScanner is required");
        this.actualOreOverlayPainter = Objects.requireNonNull(actualOreOverlayPainter, "actualOreOverlayPainter is required");
    }

    public RenderActualOreMapResult execute(RenderActualOreMapRequest request) {
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
        List<MapChunk> chunks = reader.readMapChunksAround(
                request.savePath(), center, request.radius(), mapChunkDiagnostics
        );
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        SurfaceScanResult surface = surfaceResult(
                request.savePath(), center, request.radius(), options, chunkDiagnostics
        );
        RenderedMap rendered = renderer.render(
                center, player, home, chunks, surface.blocks(), options
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
        ActualBlockMap actualOreMap = drawActualOreOverlay(
                request, rendered, center, actualOreDiagnostics
        );

        if ((hasMapRegionOverlay(options) || actualOreMap != null)
                && options.layers().contains(RenderLayer.MARKERS)) {
            systemMarkerOverlayRenderer.draw(
                    rendered.image(), center, player, home, request.radius()
            );
        }

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

        return new RenderActualOreMapResult(
                rendered.image(), rendered.report(), surface,
                environmentOverlay, geologyOverlay,
                Optional.ofNullable(actualOreMap),
                mapChunkDiagnostics, chunkDiagnostics, mapRegionDiagnostics,
                actualOreDiagnostics, userMarkersDrawn
        );
    }

    private ActualBlockMap drawActualOreOverlay(
            RenderActualOreMapRequest request,
            RenderedMap rendered,
            WorldPosition center,
            ReadDiagnostics diagnostics
    ) {
        if (request.oreMatch().isEmpty()) {
            return null;
        }
        List<ParsedChunk> chunks = reader.readChunksAround(
                request.savePath(), center, request.radius(), diagnostics
        );
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(request.savePath());
        int centerX = (int) Math.round(center.x());
        int centerZ = (int) Math.round(center.z());
        ActualBlockMap map = actualBlockMapScanner.scan(
                chunks, registry, centerX, centerZ, request.radius(),
                request.oreMatch().orElseThrow(), request.yFilter()
        );
        actualOreOverlayPainter.paint(rendered.image(), map, center, request.radius());
        return map;
    }

    private SurfaceScanResult surfaceResult(
            java.nio.file.Path savePath,
            WorldPosition center,
            int radius,
            RenderOptions options,
            ReadDiagnostics diagnostics
    ) {
        if (!options.layers().contains(RenderLayer.SURFACE)) {
            return new SurfaceScanResult(List.of(), 0, 0, 0, 0);
        }
        List<ParsedChunk> chunks = reader.readChunksAround(savePath, center, radius, diagnostics);
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath);
        return new SurfaceScanner().scan(chunks, registry, true);
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
            java.nio.file.Path savePath
    ) {
        Optional<HomeLocation> displayHome = homeStore.load(savePath);
        if (displayHome.isEmpty()) {
            return HomeState.absent();
        }
        HomeLocation location = displayHome.orElseThrow();
        WorldMetadata metadata = metadataReader.read(savePath);
        cartographer.model.WorldPosition absolute = metadata.toAbsolute(
                new cartographer.model.DisplayPosition(location.x(), 0.0, location.z())
        );
        return HomeState.present(new HomeLocation(absolute.x(), absolute.z()));
    }
}
