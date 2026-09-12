package cartographer.cli;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.marker.MarkerStore;
import cartographer.marker.UserMarker;
import cartographer.model.BlockInfo;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.EnvironmentOverlayRenderer;
import cartographer.render.GeologyOverlayRenderer;
import cartographer.render.MapRenderer;
import cartographer.render.OverlayRenderReport;
import cartographer.render.PngWriter;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
import cartographer.render.SystemMarkerOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MapCommand implements Command {

    private final PrintStream out;

    private final VcdbsReader reader;

    private final WorldMetadataReader metadataReader;

    private final HomeStore homeStore;

    private final MarkerStore markerStore;

    private final MapRenderer renderer;

    private final UserMarkerRenderer userMarkerRenderer;

    private final PngWriter pngWriter;

    private final String subcommand;

    private final EnvironmentInterpreter environmentInterpreter =
            new EnvironmentInterpreter();

    private final GeologicProvinceInterpreter geologicProvinceInterpreter =
            new GeologicProvinceInterpreter();

    private final EnvironmentOverlayRenderer environmentOverlayRenderer =
            new EnvironmentOverlayRenderer();

    private final GeologyOverlayRenderer geologyOverlayRenderer =
            new GeologyOverlayRenderer();

    private final SystemMarkerOverlayRenderer systemMarkerOverlayRenderer =
            new SystemMarkerOverlayRenderer();

    public MapCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out =
                out;

        this.reader =
                reader;

        this.metadataReader =
                metadataReader;

        this.homeStore =
                homeStore;

        this.markerStore =
                markerStore;

        this.renderer =
                renderer;

        this.userMarkerRenderer =
                userMarkerRenderer;

        this.pngWriter =
                pngWriter;

        this.subcommand =
                subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        if (!"render".equals(
                subcommand
        )) {
            throw new CommandException(
                    "Unknown map subcommand: "
                            + subcommand
            );
        }

        if (args.length < 1) {
            throw new CommandException(
                    "Usage: map render <save.vcdbs> "
                            + "--radius <blocks> "
                            + "--out <map.png>"
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
                        1024
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        1
                );

        RenderStyle style =
                RenderStyle.parse(
                        option(
                                args,
                                "--style"
                        ).orElse(
                                "simple"
                        )
                );

        RenderOptions options =
                new RenderOptions(
                        radius,
                        scale,
                        style,
                        RenderLayer.parse(
                                option(
                                        args,
                                        "--layers"
                                ).orElse(
                                        ""
                                )
                        )
                );

        Path output =
                Path.of(
                        requiredOption(
                                args,
                                "--out"
                        )
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        Optional.empty(),
                        progress
                );

        WorldPosition center =
                center(
                        args
                ).orElse(
                        player
                );

        Optional<HomeLocation> home =
                absoluteHome(
                        savePath,
                        progress
                );

        ReadDiagnostics mapChunkDiagnostics =
                new ReadDiagnostics();

        List<MapChunk> chunks =
                reader.readMapChunksAround(
                        savePath,
                        center,
                        radius,
                        mapChunkDiagnostics,
                        progress
                );

        ReadDiagnostics chunkDiagnostics =
                new ReadDiagnostics();

        SurfaceScanResult surface =
                surfaceResult(
                        savePath,
                        center,
                        radius,
                        options,
                        chunkDiagnostics,
                        progress
                );

        RenderedMap rendered =
                renderer.render(
                        center,
                        player,
                        home,
                        chunks,
                        surface.blocks(),
                        options,
                        progress
                );

        ReadDiagnostics mapRegionDiagnostics =
                new ReadDiagnostics();

        List<ServerMapRegion> mapRegions =
                mapRegions(
                        savePath,
                        options,
                        mapRegionDiagnostics,
                        progress
                );

        OverlayRenderReport environmentOverlay =
                drawEnvironmentOverlay(
                        rendered,
                        center,
                        radius,
                        options,
                        mapRegions,
                        progress
                );

        OverlayRenderReport geologyOverlay =
                drawGeologyOverlay(
                        rendered,
                        center,
                        radius,
                        options,
                        mapRegions,
                        progress
                );

        if (hasMapRegionOverlay(
                options
        )
                && options.layers()
                .contains(
                        RenderLayer.MARKERS
                )) {

            progress.start(
                    "Redrawing system markers"
            );

            systemMarkerOverlayRenderer.draw(
                    rendered.image(),
                    center,
                    player,
                    home,
                    radius
            );

            progress.done(
                    "System markers redrawn"
            );
        }

        int userMarkersDrawn =
                drawUserMarkers(
                        savePath,
                        rendered,
                        center,
                        radius,
                        options,
                        progress
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

        printReport(
                output,
                rendered,
                userMarkersDrawn,
                options,
                mapChunkDiagnostics,
                chunkDiagnostics,
                mapRegionDiagnostics,
                surface,
                environmentOverlay,
                geologyOverlay
        );

        return 0;
    }

    private List<ServerMapRegion> mapRegions(
            Path savePath,
            RenderOptions options,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        if (!hasMapRegionOverlay(
                options
        )) {

            return List.of();
        }

        return reader.readMapRegions(
                savePath,
                diagnostics,
                progress
        );
    }

    private OverlayRenderReport drawEnvironmentOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            List<ServerMapRegion> mapRegions,
            ProgressReporter progress
    ) {
        if (!options.layers()
                .contains(
                        RenderLayer.ENVIRONMENT
                )) {

            return OverlayRenderReport.none();
        }

        progress.start(
                "Drawing environment overlay"
        );

        List<EnvironmentProfile> profiles =
                mapRegions.stream()
                        .map(
                                environmentInterpreter::interpret
                        )
                        .toList();

        OverlayRenderReport report =
                environmentOverlayRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        profiles
                );

        progress.done(
                "Environment overlay drawn"
        );

        return report;
    }

    private OverlayRenderReport drawGeologyOverlay(
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            List<ServerMapRegion> mapRegions,
            ProgressReporter progress
    ) {
        if (!options.layers()
                .contains(
                        RenderLayer.GEOLOGY
                )) {

            return OverlayRenderReport.none();
        }

        progress.start(
                "Drawing geology overlay"
        );

        List<GeologicProvinceSummary> summaries =
                mapRegions.stream()
                        .map(
                                geologicProvinceInterpreter::summarize
                        )
                        .flatMap(
                                Optional::stream
                        )
                        .toList();

        OverlayRenderReport report =
                geologyOverlayRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        summaries
                );

        progress.done(
                "Geology overlay drawn"
        );

        return report;
    }

    private boolean hasMapRegionOverlay(
            RenderOptions options
    ) {
        return options.layers()
                .contains(
                        RenderLayer.ENVIRONMENT
                )
                || options.layers()
                .contains(
                        RenderLayer.GEOLOGY
                );
    }

    private void printReport(
            Path output,
            RenderedMap rendered,
            int userMarkersDrawn,
            RenderOptions options,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            ReadDiagnostics mapRegionDiagnostics,
            SurfaceScanResult surface,
            OverlayRenderReport environmentOverlay,
            OverlayRenderReport geologyOverlay
    ) {
        out.println(
                "MAP"
        );

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "Image: "
                        + rendered.report()
                        .width()
                        + "x"
                        + rendered.report()
                        .height()
        );

        out.println(
                "Style: "
                        + rendered.report()
                        .style()
        );

        out.println(
                "Layers: "
                        + rendered.report()
                        .layers()
        );

        out.println(
                "Tiles drawn: "
                        + rendered.report()
                        .tilesDrawn()
        );

        out.println(
                "System markers: "
                        + rendered.report()
                        .markerCount()
        );

        out.println(
                "User markers: "
                        + userMarkersDrawn
        );

        out.println(
                "Parsed mapchunks: "
                        + mapChunkDiagnostics.parsed()
        );

        out.println(
                "Skipped mapchunks: "
                        + mapChunkDiagnostics.skipped()
        );

        out.println(
                "Failed mapchunks: "
                        + mapChunkDiagnostics.failed()
        );

        printFailureReasons(
                "Mapchunk failure reasons",
                mapChunkDiagnostics
        );

        if (options.layers()
                .contains(
                        RenderLayer.SURFACE
                )) {

            out.println(
                    "Parsed chunks: "
                            + chunkDiagnostics.parsed()
            );

            out.println(
                    "Skipped chunks: "
                            + chunkDiagnostics.skipped()
            );

            out.println(
                    "Failed chunks: "
                            + chunkDiagnostics.failed()
            );

            printFailureReasons(
                    "Chunk failure reasons",
                    chunkDiagnostics
            );

            printLiquidFailureReasons(
                    chunkDiagnostics
            );

            out.println(
                    "Surface columns: "
                            + surface.columnsScanned()
            );

            out.println(
                    "Liquid unavailable columns: "
                            + surface.liquidUnavailableColumns()
            );

            out.println(
                    "Water columns: "
                            + surface.waterColumns()
            );

            out.println(
                    "Unknown surface blocks: "
                            + surface.unknownSurfaceBlocks()
            );

            printTopUnknownSurfaceBlockCodes(
                    surface
            );

            out.println(
                    "Distinct surface block codes: "
                            + surface.distinctSurfaceBlockCodes(
                            20
                    )
            );
        }

        if (hasMapRegionOverlay(
                options
        )) {

            out.println(
                    "Parsed mapregions: "
                            + mapRegionDiagnostics.parsed()
            );

            out.println(
                    "Skipped mapregions: "
                            + mapRegionDiagnostics.skipped()
            );

            out.println(
                    "Failed mapregions: "
                            + mapRegionDiagnostics.failed()
            );

            printFailureReasons(
                    "Mapregion failure reasons",
                    mapRegionDiagnostics
            );
        }

        if (options.layers()
                .contains(
                        RenderLayer.ENVIRONMENT
                )) {

            printOverlayReport(
                    "Environment overlay",
                    environmentOverlay
            );
        }

        if (options.layers()
                .contains(
                        RenderLayer.GEOLOGY
                )) {

            printOverlayReport(
                    "Geology overlay",
                    geologyOverlay
            );
        }

        printNotes(
                mapChunkDiagnostics
        );

        printNotes(
                chunkDiagnostics
        );

        printNotes(
                mapRegionDiagnostics
        );
    }

    private void printOverlayReport(
            String name,
            OverlayRenderReport report
    ) {
        out.println(
                name
                        + ": candidates="
                        + report.candidates()
                        + " drawn="
                        + report.drawn()
                        + " unavailable="
                        + report.unavailable()
        );
    }

    private int drawUserMarkers(
            Path savePath,
            RenderedMap rendered,
            WorldPosition center,
            int radius,
            RenderOptions options,
            ProgressReporter progress
    ) {
        if (!options.layers()
                .contains(
                        RenderLayer.MARKERS
                )) {

            return 0;
        }

        List<UserMarker> markers =
                markerStore.load(
                        savePath
                );

        if (markers.isEmpty()) {
            return 0;
        }

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        progress.start(
                "Drawing user markers"
        );

        int count =
                userMarkerRenderer.draw(
                        rendered.image(),
                        center,
                        radius,
                        markers,
                        metadata
                );

        progress.done(
                "User markers drawn"
        );

        return count;
    }

    private void printFailureReasons(
            String title,
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                title
                        + ":"
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

    private void printLiquidFailureReasons(
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.liquidFailureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                "Liquid decode failures: "
                        + diagnostics.liquidDecodeFailures()
        );

        out.println(
                "Liquid failure reasons:"
        );

        diagnostics.liquidFailureReasonLines()
                .forEach(
                        line ->
                                out.println(
                                        "  "
                                                + line
                                )
                );
    }

    private void printTopUnknownSurfaceBlockCodes(
            SurfaceScanResult result
    ) {
        if (result.unknownSurfaceBlocks()
                <= 0) {

            return;
        }

        out.println(
                "Top UNKNOWN surface block codes:"
        );

        result.topUnknownSurfaceBlockCodes(
                        10
                )
                .forEach(
                        block ->
                                out.println(
                                        "  "
                                                + block.code()
                                                + ": "
                                                + block.count()
                                )
                );
    }

    private void printNotes(
            ReadDiagnostics diagnostics
    ) {
        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private SurfaceScanResult surfaceResult(
            Path savePath,
            WorldPosition center,
            int radius,
            RenderOptions options,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        if (!options.layers()
                .contains(
                        RenderLayer.SURFACE
                )) {

            return new SurfaceScanResult(
                    List.of(),
                    0,
                    0,
                    0,
                    0
            );
        }

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

        return new SurfaceScanner()
                .scan(
                        chunks,
                        registry,
                        true,
                        progress
                );
    }

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue
    ) {
        Optional<String> option =
                option(
                        args,
                        optionName
                );

        if (option.isEmpty()) {
            return defaultValue;
        }

        try {

            return getValue(
                    optionName,
                    option.orElseThrow()
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + option.orElse("")
            );
        }
    }

    private static int getValue(String optionName, String option) {
        int value =
                Integer.parseInt(
                        option
                );

        int max =
                "--scale".equals(
                        optionName
                )
                        ? 16
                        : 8192;

        if (value <= 0
                || value > max) {

            throw new CommandException(
                    optionName
                            + " must be between 1 and "
                            + max
            );
        }
        return value;
    }

    private String requiredOption(
            String[] args,
            String optionName
    ) {
        return option(
                args,
                optionName
        ).orElseThrow(
                () ->
                        new CommandException(
                                "Missing option: "
                                        + optionName
                        )
        );
    }

    private Optional<String> option(
            String[] args,
            String optionName
    ) {
        for (int index = 1;
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

        String centerX =
                x.orElseThrow();

        String centerZ =
                z.orElseThrow();

        return Optional.of(
                new WorldPosition(
                        parseDouble(
                                centerX,
                                "--center-x"
                        ),
                        0.0,
                        parseDouble(
                                centerZ,
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

    private Optional<HomeLocation> absoluteHome(
            Path savePath,
            ProgressReporter progress
    ) {
        Optional<HomeLocation> displayHome =
                homeStore.load(
                        savePath
                );

        if (displayHome.isEmpty()) {
            return Optional.empty();
        }

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        HomeLocation home =
                displayHome.orElseThrow();

        WorldPosition absolute =
                metadata.toAbsolute(
                        new DisplayPosition(
                                home.x(),
                                0.0,
                                home.z()
                        )
                );

        return Optional.of(
                new HomeLocation(
                        absolute.x(),
                        absolute.z()
                )
        );
    }
}
