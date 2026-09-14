package cartographer.cli;

import cartographer.application.InspectSurfaceObjectsRequest;
import cartographer.application.InspectSurfaceObjectsResult;
import cartographer.application.InspectSurfaceObjectsUseCase;
import cartographer.application.SurfaceMaterialMatch;
import cartographer.model.BlockInfo;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
import cartographer.render.ResourceOverlayRenderer;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceCandidate;
import cartographer.resource.ResourceHotspot;
import cartographer.resource.ResourceOverlayCell;
import cartographer.resource.ResourceSummary;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.resource.SurfaceResourceDeposit;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ResourceCommand implements Command {

    private static final int DEFAULT_TOP =
            20;

    private static final int MAX_TOP =
            100;

    private static final int DEFAULT_HOTSPOT_SEPARATION =
            256;

    private static final int DEFAULT_RENDER_RADIUS =
            1024;

    private static final int DEFAULT_SURFACE_RADIUS =
            512;

    private static final double DEFAULT_MINIMUM_SIGNAL =
            0.50;

    private final PrintStream out;
    private final VcdbsReader reader;
    private final ResourceAnalyzer analyzer;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final MapRenderer mapRenderer;
    private final ResourceOverlayRenderer overlayRenderer;
    private final PngWriter pngWriter;
    private final InspectSurfaceObjectsUseCase surfaceObjectInspectionUseCase;

    private final SurfaceScanner surfaceScanner =
            new SurfaceScanner();

    private final SurfaceResourceAnalyzer surfaceResourceAnalyzer =
            new SurfaceResourceAnalyzer();

    private final SurfaceResourceOverlayRenderer surfaceResourceOverlayRenderer =
            new SurfaceResourceOverlayRenderer();

    private final String subcommand;

    public ResourceCommand(
            PrintStream out,
            VcdbsReader reader,
            ResourceAnalyzer analyzer,
            String subcommand
    ) {
        this(
                out,
                reader,
                analyzer,
                new WorldMetadataReader(),
                defaultHomeStore(),
                new MapRenderer(),
                new ResourceOverlayRenderer(),
                new PngWriter(),
                subcommand
        );
    }

    public ResourceCommand(
            PrintStream out,
            VcdbsReader reader,
            ResourceAnalyzer analyzer,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MapRenderer mapRenderer,
            ResourceOverlayRenderer overlayRenderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.analyzer = analyzer;
        this.metadataReader = metadataReader;
        this.homeStore = homeStore;
        this.mapRenderer = mapRenderer;
        this.overlayRenderer = overlayRenderer;
        this.pngWriter = pngWriter;
        this.surfaceObjectInspectionUseCase = new InspectSurfaceObjectsUseCase(
                reader,
                metadataReader
        );
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        switch (subcommand) {
            case "list" ->
                    list(
                            args
                    );

            case "inspect" ->
                    inspect(
                            args
                    );

            case "search" ->
                    search(
                            args
                    );

            case "render" ->
                    render(
                            args
                    );

            case "surface-search" ->
                    surfaceSearch(
                            args
                    );

            case "surface-inspect" ->
                    surfaceInspect(
                            args
                    );

            case "surface-render" ->
                    surfaceRender(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown resource subcommand: "
                                    + subcommand
                    );
        }
    }

    private void list(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: resource list <save.vcdbs>"
            );
        }

        LoadedResources loaded =
                load(
                        Path.of(
                                args[0]
                        )
                );

        out.println(
                "RESOURCE LIST"
        );

        printDiagnostics(
                loaded.diagnostics()
        );

        List<String> keys =
                analyzer.resourceKeys(
                        loaded.regions()
                );

        if (keys.isEmpty()) {
            out.println(
                    "Resource maps: none"
            );

        } else {
            out.println(
                    "Resource maps:"
            );

            keys.forEach(
                    key ->
                            out.println(
                                    "  "
                                            + key
                            )
            );
        }
    }

    private void inspect(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource inspect <save.vcdbs> <resource>"
            );
        }

        LoadedResources loaded =
                load(
                        Path.of(
                                args[0]
                        )
                );

        out.println(
                "RESOURCE INSPECT"
        );

        printDiagnostics(
                loaded.diagnostics()
        );

        Optional<String> selected =
                selectResource(
                        loaded.regions(),
                        args[1]
                );

        if (selected.isEmpty()) {
            return;
        }

        String resourceKey =
                selected.orElseThrow();

        ResourceSummary summary =
                analyzer.summarize(
                                loaded.regions(),
                                resourceKey,
                                10
                        )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Resource map disappeared during inspection: "
                                                        + resourceKey
                                        )
                        );

        printSummary(
                summary
        );
    }

    private void search(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource search <save.vcdbs> <resource> "
                            + "[--top <n>] [--separation <blocks>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        int top =
                intOption(
                        args,
                        "--top",
                        DEFAULT_TOP,
                        MAX_TOP
                );

        int separation =
                intOption(
                        args,
                        "--separation",
                        DEFAULT_HOTSPOT_SEPARATION,
                        8192
                );

        LoadedResources loaded =
                load(
                        savePath
                );

        out.println(
                "RESOURCE SEARCH"
        );

        printDiagnostics(
                loaded.diagnostics()
        );

        Optional<String> selected =
                selectResource(
                        loaded.regions(),
                        args[1]
                );

        if (selected.isEmpty()) {
            return;
        }

        String resourceKey =
                selected.orElseThrow();

        List<ResourceHotspot> hotspots =
                analyzer.hotspots(
                        loaded.regions(),
                        resourceKey,
                        top,
                        separation
                );

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        ProgressReporter.NONE
                );

        out.println(
                "Resource: "
                        + resourceKey
        );

        out.println(
                "Independent hotspots:"
        );

        out.println(
                "Minimum separation: "
                        + separation
                        + " blocks"
        );

        if (hotspots.isEmpty()) {
            out.println(
                    "  none"
            );

            return;
        }

        for (int index = 0;
             index < hotspots.size();
             index++) {

            ResourceHotspot hotspot =
                    hotspots.get(
                            index
                    );

            DisplayPosition display =
                    metadata.toDisplay(
                            new WorldPosition(
                                    hotspot.approximateWorldX(),
                                    0.0,
                                    hotspot.approximateWorldZ()
                            )
                    );

            out.printf(
                    Locale.ROOT,
                    "%d. region %d,%d cell %d,%d raw=%d relativeSignal=%.3f%n",
                    index + 1,
                    hotspot.peakRegion().x(),
                    hotspot.peakRegion().z(),
                    hotspot.peakLocalX(),
                    hotspot.peakLocalZ(),
                    hotspot.peakRawValue(),
                    hotspot.relativeIntensity()
            );

            out.printf(
                    Locale.ROOT,
                    "   absolute world center: %d,%d%n",
                    hotspot.approximateWorldX(),
                    hotspot.approximateWorldZ()
            );

            out.printf(
                    Locale.ROOT,
                    "   display center: %.0f,%.0f%n",
                    display.x(),
                    display.z()
            );
        }
    }

    private void render(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource render <save.vcdbs> <resource> "
                            + "[--radius <blocks>] "
                            + "[--out <map.png>] "
                            + "[--min-signal <0..1>] "
                            + "[--scale <n>] "
                            + "[--style simple|topographic|high-contrast] "
                            + "[--center-x <x> --center-z <z>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        int radius =
                intOption(
                        args,
                        "--radius",
                        DEFAULT_RENDER_RADIUS,
                        8192
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        1,
                        16
                );

        double minimumSignal =
                minimumSignalOption(
                        args
                );

        RenderStyle style =
                RenderStyle.parse(
                        option(
                                args,
                                "--style"
                        ).orElse(
                                "topographic"
                        )
                );

        LoadedResources loaded =
                load(
                        savePath
                );

        Optional<String> selected =
                selectResource(
                        loaded.regions(),
                        args[1]
                );

        if (selected.isEmpty()) {
            return;
        }

        String resourceKey =
                selected.orElseThrow();

        Path output =
                option(
                        args,
                        "--out"
                )
                        .map(
                                Path::of
                        )
                        .orElse(
                                Path.of(
                                        "output",
                                        resourceKey
                                                + "-search.png"
                                )
                        );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        progress
                );

        WorldPosition center =
                center(
                        args
                )
                        .orElse(
                                player
                        );

        HomeState home =
                absoluteHome(
                        savePath,
                        progress
                );

        ReadDiagnostics mapDiagnostics =
                new ReadDiagnostics();

        List<MapChunk> chunks =
                reader.readMapChunksAround(
                        savePath,
                        center,
                        radius,
                        mapDiagnostics,
                        progress
                );

        RenderOptions renderOptions =
                terrainRenderOptions(
                        radius,
                        scale,
                        style
                );

        RenderedMap rendered =
                mapRenderer.render(
                        center,
                        player,
                        home,
                        chunks,
                        List.of(),
                        renderOptions,
                        progress
                );

        List<ResourceOverlayCell> overlayCells =
                analyzer.overlayCells(
                        loaded.regions(),
                        resourceKey,
                        minimumSignal
                );

        progress.start(
                "Drawing resource overlay"
        );

        int overlayCellsDrawn =
                overlayRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        overlayCells,
                        resourceKey,
                        minimumSignal,
                        player,
                        home
                );

        progress.done(
                "Resource overlay drawn"
        );

        progress.start(
                "Writing PNG"
        );

        pngWriter.write(
                rendered.image(),
                output
        );

        progress.done(
                "PNG written"
        );

        out.println(
                "RESOURCE MAP"
        );

        out.println(
                "Resource: "
                        + resourceKey
        );

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "Image: "
                        + rendered.report().width()
                        + "x"
                        + rendered.report().height()
        );

        out.println(
                "Radius: "
                        + radius
                        + " blocks"
        );

        out.printf(
                Locale.ROOT,
                "Minimum relative signal: %.3f%n",
                minimumSignal
        );

        out.println(
                "Overlay cells available: "
                        + overlayCells.size()
        );

        out.println(
                "Overlay cells drawn: "
                        + overlayCellsDrawn
        );

        out.println(
                "Parsed mapchunks: "
                        + mapDiagnostics.parsed()
        );

        out.println(
                "Skipped mapchunks: "
                        + mapDiagnostics.skipped()
        );

        out.println(
                "Failed mapchunks: "
                        + mapDiagnostics.failed()
        );

        printFailureReasons(
                mapDiagnostics
        );
    }

    private void surfaceSearch(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource surface-search <save.vcdbs> <match> "
                            + "[--radius <blocks>] "
                            + "[--top <n>] "
                            + "[--center-x <x> --center-z <z>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String match =
                args[1];

        int radius =
                intOption(
                        args,
                        "--radius",
                        DEFAULT_SURFACE_RADIUS,
                        8192
                );

        int top =
                intOption(
                        args,
                        "--top",
                        DEFAULT_TOP,
                        MAX_TOP
                );

        SurfaceResourceLoad loaded =
                loadSurfaceResource(
                        savePath,
                        match,
                        radius,
                        args
                );

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        ProgressReporter.NONE
                );

        out.println(
                "SURFACE RESOURCE SEARCH"
        );

        out.println(
                "Match: "
                        + loaded.analysis()
                        .query()
        );

        out.println(
                "Radius: "
                        + radius
                        + " blocks"
        );

        out.println(
                "Parsed chunks: "
                        + loaded.chunkDiagnostics()
                        .parsed()
        );

        out.println(
                "Failed chunks: "
                        + loaded.chunkDiagnostics()
                        .failed()
        );

        printFailureReasons(
                loaded.chunkDiagnostics()
        );

        out.println(
                "Surface columns: "
                        + loaded.surface()
                        .columnsScanned()
        );

        out.println(
                "Matching surface blocks: "
                        + loaded.analysis()
                        .matchingBlockCount()
        );

        out.println(
                "Connected deposits: "
                        + loaded.analysis()
                        .depositCount()
        );

        List<SurfaceResourceDeposit> deposits =
                loaded.analysis()
                        .deposits();

        if (deposits.isEmpty()) {
            out.println(
                    "Deposits: none"
            );

            return;
        }

        out.println(
                "Largest deposits:"
        );

        int count =
                Math.min(
                        top,
                        deposits.size()
                );

        for (int index = 0;
             index < count;
             index++) {

            SurfaceResourceDeposit deposit =
                    deposits.get(
                            index
                    );

            DisplayPosition display =
                    metadata.toDisplay(
                            new WorldPosition(
                                    deposit.centerWorldX(),
                                    0.0,
                                    deposit.centerWorldZ()
                            )
                    );

            out.printf(
                    Locale.ROOT,
                    "%d. blocks=%d size=%dx%d y=%d..%d%n",
                    index + 1,
                    deposit.blockCount(),
                    deposit.widthBlocks(),
                    deposit.depthBlocks(),
                    deposit.minY(),
                    deposit.maxY()
            );

            out.println(
                    "   codes: "
                            + deposit.blockCodes()
            );

            out.printf(
                    Locale.ROOT,
                    "   absolute center: %.0f,%.0f%n",
                    deposit.centerWorldX(),
                    deposit.centerWorldZ()
            );

            out.printf(
                    Locale.ROOT,
                    "   display center: %.0f,%.0f%n",
                    display.x(),
                    display.z()
            );

            out.printf(
                    Locale.ROOT,
                    "   bounds: x=%d..%d z=%d..%d%n",
                    deposit.minWorldX(),
                    deposit.maxWorldX(),
                    deposit.minWorldZ(),
                    deposit.maxWorldZ()
            );
        }
    }

    private void surfaceInspect(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource surface-inspect <save.vcdbs> <match> "
                            + "[--radius <blocks>] [--center-x <x> --center-z <z>]"
            );
        }

        Path savePath = Path.of(args[0]);
        String resourceKey = args[1];
        int radius = intOption(args, "--radius", DEFAULT_SURFACE_RADIUS, 8192);
        InspectSurfaceObjectsResult result = surfaceObjectInspectionUseCase.execute(
                new InspectSurfaceObjectsRequest(
                        savePath,
                        resourceKey,
                        radius,
                        center(args)
                )
        );

        out.println("SURFACE OBJECT INSPECT");
        out.println("Resource: " + resourceKey);
        out.println("Center: " + result.center().x() + "," + result.center().z());
        out.println("Radius: " + radius);
        out.println("Registry matches: " + result.registryMatches().size());
        for (BlockInfo block : result.registryMatches()) {
            out.println("  id=" + block.id() + " " + block.code());
        }
        if (result.registryMatches().isEmpty()) {
            out.println("No block registry codes matched the surface resource families.");
        }
        out.println("Targets planned: " + result.plan().targets().size());
        out.println("Chunk positions requested: " + result.plan().chunkPositions().size());
        out.println("Chunk outcomes:");
        out.println("  decoded: " + result.chunkStats().fullyDecodedChunks());
        out.println("  palette rejected: " + result.chunkStats().paletteRejectedChunks());
        out.println("  missing: " + (result.chunkStats().uniquePositionsRequested()
                - result.chunkStats().rowsFound()));
        out.println("  failed: " + result.chunkStats().failedChunks());
        out.println("Observed: " + result.scan().observedTargets());
        out.println("Not observed: " + result.scan().notObservedTargets());
        out.println("Unavailable: " + result.scan().unavailablePositions());
        out.println("Observations:");
        result.scan().blocks().stream().limit(100).forEach(block -> out.println(
                "  " + block.blockInfo().code() + " @ X=" + block.worldX()
                        + " Y=" + block.y() + " Z=" + block.worldZ()
        ));
    }

    private void surfaceRender(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource surface-render <save.vcdbs> <match> "
                            + "[--radius <blocks>] "
                            + "[--out <map.png>] "
                            + "[--scale <n>] "
                            + "[--style simple|topographic|high-contrast] "
                            + "[--center-x <x> --center-z <z>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String match =
                args[1];

        int radius =
                intOption(
                        args,
                        "--radius",
                        DEFAULT_SURFACE_RADIUS,
                        8192
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        1,
                        16
                );

        RenderStyle style =
                RenderStyle.parse(
                        option(
                                args,
                                "--style"
                        ).orElse(
                                "topographic"
                        )
                );

        Path output =
                option(
                        args,
                        "--out"
                )
                        .map(
                                Path::of
                        )
                        .orElse(
                                Path.of(
                                        "output",
                                        safeFileName(
                                                match
                                        )
                                                + "-surface-search.png"
                                )
                        );

        SurfaceResourceLoad loaded =
                loadSurfaceResource(
                        savePath,
                        match,
                        radius,
                        args
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        HomeState home =
                absoluteHome(
                        savePath,
                        progress
                );

        ReadDiagnostics mapDiagnostics =
                new ReadDiagnostics();

        List<MapChunk> mapChunks =
                reader.readMapChunksAround(
                        savePath,
                        loaded.center(),
                        radius,
                        mapDiagnostics,
                        progress
                );

        RenderedMap rendered =
                mapRenderer.render(
                        loaded.center(),
                        loaded.player(),
                        home,
                        mapChunks,
                        List.of(),
                        terrainRenderOptions(
                                radius,
                                scale,
                                style
                        ),
                        progress
                );

        progress.start(
                "Drawing surface resource overlay"
        );

        int blocksDrawn =
                surfaceResourceOverlayRenderer.draw(
                        rendered.image(),
                        loaded.center(),
                        radius,
                        loaded.analysis(),
                        loaded.player(),
                        home
                );

        progress.done(
                "Surface resource overlay drawn"
        );

        progress.start(
                "Writing PNG"
        );

        pngWriter.write(
                rendered.image(),
                output
        );

        progress.done(
                "PNG written"
        );

        out.println(
                "SURFACE RESOURCE MAP"
        );

        out.println(
                "Match: "
                        + loaded.analysis()
                        .query()
        );

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "Image: "
                        + rendered.report().width()
                        + "x"
                        + rendered.report().height()
        );

        out.println(
                "Radius: "
                        + radius
                        + " blocks"
        );

        out.println(
                "Matching blocks: "
                        + loaded.analysis()
                        .matchingBlockCount()
        );

        out.println(
                "Connected deposits: "
                        + loaded.analysis()
                        .depositCount()
        );

        out.println(
                "Overlay blocks drawn: "
                        + blocksDrawn
        );

        out.println(
                "Parsed chunks: "
                        + loaded.chunkDiagnostics()
                        .parsed()
        );

        out.println(
                "Failed chunks: "
                        + loaded.chunkDiagnostics()
                        .failed()
        );

        out.println(
                "Parsed mapchunks: "
                        + mapDiagnostics.parsed()
        );

        out.println(
                "Failed mapchunks: "
                        + mapDiagnostics.failed()
        );
    }

    private SurfaceResourceLoad loadSurfaceResource(
            Path savePath,
            String match,
            int radius,
            String[] args
    ) {
        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        progress
                );

        WorldPosition center =
                center(
                        args
                )
                        .orElse(
                                player
                        );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        List<ParsedChunk> chunks =
                reader.readChunksAround(
                        savePath,
                        center,
                        radius,
                        diagnostics,
                        progress
                );

        Map<Integer, BlockInfo> registry =
                reader.readBlockRegistry(
                        savePath,
                        progress
                );

        SurfaceScanResult surface =
                surfaceScanner.scan(
                        chunks,
                        registry,
                        true,
                        progress
                );

        SurfaceMaterialMatch surfaceMatch = surfaceMatch(match);
        SurfaceResourceAnalysis analysis = surfaceResourceAnalyzer.analyzeMatched(
                surfaceMatch.displayName(),
                surfaceMatch.matchingBlocks(surface.blocks()),
                surface.columnsScanned()
        );

        return new SurfaceResourceLoad(
                player,
                center,
                diagnostics,
                surface,
                analysis
        );
    }

    private SurfaceMaterialMatch surfaceMatch(String value) {
        return new SurfaceMaterialMatch(value, List.of(value));
    }

    private RenderOptions terrainRenderOptions(
            int radius,
            int scale,
            RenderStyle style
    ) {
        return new RenderOptions(
                radius,
                scale,
                style,
                EnumSet.of(
                        RenderLayer.TERRAIN
                )
        );
    }

    private LoadedResources load(
            Path savePath
    ) {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        List<ServerMapRegion> regions =
                reader.readMapRegions(
                        savePath,
                        diagnostics,
                        progress
                );

        return new LoadedResources(
                regions,
                diagnostics
        );
    }

    private Optional<String> selectResource(
            List<ServerMapRegion> regions,
            String query
    ) {
        List<String> matches =
                analyzer.matchingKeys(
                        regions,
                        query
                );

        if (matches.isEmpty()) {
            out.println(
                    "Resource: "
                            + query
            );

            out.println(
                    "Matching resource maps: none"
            );

            printAvailableResources(
                    regions
            );

            return Optional.empty();
        }

        if (matches.size() > 1) {
            out.println(
                    "Resource: "
                            + query
            );

            out.println(
                    "Matching resource maps are ambiguous:"
            );

            matches.forEach(
                    match ->
                            out.println(
                                    "  "
                                            + match
                            )
            );

            return Optional.empty();
        }

        return Optional.of(
                matches.getFirst()
        );
    }

    private void printSummary(
            ResourceSummary summary
    ) {
        out.println(
                "Resource: "
                        + summary.resourceKey()
        );

        out.println(
                "Regions with map: "
                        + summary.regions()
        );

        out.println(
                "Inner cells: "
                        + summary.cells()
        );

        out.println(
                "Raw min: "
                        + summary.rawMin()
        );

        out.println(
                "Raw max: "
                        + summary.rawMax()
        );

        out.printf(
                Locale.ROOT,
                "Raw average: %.3f%n",
                summary.averageRawValue()
        );

        out.println(
                "Distinct raw values: "
                        + summary.distinctValues()
        );

        out.println(
                "Strongest candidate sample:"
        );

        if (summary.strongestCandidates()
                .isEmpty()) {

            out.println(
                    "  none"
            );
        }

        for (ResourceCandidate candidate :
                summary.strongestCandidates()) {

            out.printf(
                    Locale.ROOT,
                    "  region %d,%d cell %d,%d raw=%d relativeSignal=%.3f approximateWorldCenter=%d,%d%n",
                    candidate.regionCoordinate().x(),
                    candidate.regionCoordinate().z(),
                    candidate.localX(),
                    candidate.localZ(),
                    candidate.rawValue(),
                    candidate.relativeIntensity(),
                    candidate.approximateWorldX(),
                    candidate.approximateWorldZ()
            );
        }
    }

    private void printAvailableResources(
            List<ServerMapRegion> regions
    ) {
        List<String> keys =
                analyzer.resourceKeys(
                        regions
                );

        if (keys.isEmpty()) {
            return;
        }

        out.println(
                "Available resource maps:"
        );

        keys.forEach(
                key ->
                        out.println(
                                "  "
                                        + key
                        )
        );
    }

    private void printDiagnostics(
            ReadDiagnostics diagnostics
    ) {
        out.println(
                "Regions parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Regions skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Regions failed: "
                        + diagnostics.failed()
        );

        printFailureReasons(
                diagnostics
        );
    }

    private void printFailureReasons(
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                "Failure reasons:"
        );

        diagnostics.failureReasonLines()
                .forEach(
                        line ->
                                out.println(
                                        "  "
                                                + line
                                )
                );
    }

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue,
            int maximum
    ) {
        Optional<String> value =
                option(
                        args,
                        optionName
                );

        if (value.isEmpty()) {
            return defaultValue;
        }

        try {
            int parsed =
                    Integer.parseInt(
                            value.orElseThrow()
                    );

            if (parsed < 1
                    || parsed > maximum) {

                throw new CommandException(
                        optionName
                                + " must be between 1 and "
                                + maximum
                );
            }

            return parsed;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value.orElse("")
            );
        }
    }

    private double minimumSignalOption(
            String[] args
    ) {
        Optional<String> value =
                option(
                        args,
                        "--min-signal"
                );

        if (value.isEmpty()) {
            return DEFAULT_MINIMUM_SIGNAL;
        }

        try {
            double parsed =
                    Double.parseDouble(
                            value.orElseThrow()
                    );

            if (!Double.isFinite(
                    parsed
            )
                    || parsed < 0.0
                    || parsed > 1.0) {

                throw new CommandException(
                        "--min-signal must be between 0.0 and 1.0"
                );
            }

            return parsed;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid --min-signal: "
                            + value.orElse("")
            );
        }
    }

    private Optional<String> option(
            String[] args,
            String optionName
    ) {
        for (int index = 0;
             index < args.length - 1;
             index++) {

            if (optionName.equals(
                    args[index]
            )) {
                return Optional.of(
                        args[index + 1]
                );
            }
        }

        return Optional.empty();
    }

    private Optional<WorldPosition> center(
            String[] args
    ) {
        Optional<String> x =
                option(
                        args,
                        "--center-x"
                );

        Optional<String> z =
                option(
                        args,
                        "--center-z"
                );

        if (x.isEmpty()
                && z.isEmpty()) {

            return Optional.empty();
        }

        if (x.isEmpty()
                || z.isEmpty()) {

            throw new CommandException(
                    "--center-x and --center-z must be used together"
            );
        }

        return Optional.of(
                new WorldPosition(
                        parseDouble(
                                x.orElseThrow(),
                                "--center-x"
                        ),
                        0.0,
                        parseDouble(
                                z.orElseThrow(),
                                "--center-z"
                        )
                )
        );
    }

    private double parseDouble(
            String value,
            String optionName
    ) {
        try {
            return Double.parseDouble(
                    value
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value
            );
        }
    }

    private HomeState absoluteHome(
            Path savePath,
            ProgressReporter progress
    ) {
        Optional<HomeLocation> displayHome =
                homeStore.load(
                        savePath
                );

        if (displayHome.isEmpty()) {
            return HomeState.absent();
        }

        HomeLocation location =
                displayHome.orElseThrow();

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        WorldPosition absolute =
                metadata.toAbsolute(
                        new DisplayPosition(
                                location.x(),
                                0.0,
                                location.z()
                        )
                );

        return HomeState.present(
                new HomeLocation(
                        absolute.x(),
                        absolute.z()
                )
        );
    }

    private String safeFileName(
            String value
    ) {
        String safe =
                value == null
                        ? ""
                        : value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9._-]+",
                                "-"
                        );

        if (safe.isBlank()) {
            return "surface-resource";
        }

        return safe;
    }

    private static HomeStore defaultHomeStore() {
        Path configDirectory =
                Path.of(
                        System.getProperty(
                                "user.home"
                        ),
                        ".vs-cartographer"
                );

        return new HomeStore(
                configDirectory.resolve(
                        "home.properties"
                )
        );
    }

    private record LoadedResources(
            List<ServerMapRegion> regions,
            ReadDiagnostics diagnostics
    ) {
    }

    private record SurfaceResourceLoad(
            WorldPosition player,
            WorldPosition center,
            ReadDiagnostics chunkDiagnostics,
            SurfaceScanResult surface,
            SurfaceResourceAnalysis analysis
    ) {
    }
}
