package cartographer.cli;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.model.WorldPosition;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderReport;
import cartographer.render.MapRenderer;
import cartographer.render.OverlayRenderReport;
import cartographer.render.PngWriter;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.perf.RenderDataCacheStore;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.SurfaceDiagnosticsSummary;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

public class MapCommand implements Command {

    private final PrintStream out;
    private final PngWriter pngWriter;
    private final RenderActualOreMapUseCase renderActualOreMapUseCase;
    private final String subcommand;

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
        this(
                out,
                reader,
                metadataReader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                pngWriter,
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter(),
                subcommand
        );
    }

    public MapCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            PngWriter pngWriter,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter,
            String subcommand
    ) {
        this.out = out;
        this.pngWriter = pngWriter;
        this.renderActualOreMapUseCase = new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                actualBlockMapScanner,
                actualOreOverlayPainter
        );
        this.subcommand = subcommand;
    }

    public MapCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            PngWriter pngWriter,
            RenderDataCacheStore renderDataCacheStore,
            String subcommand
    ) {
        this(
                out,
                reader,
                metadataReader,
                homeStore,
                markerStore,
                renderer,
                userMarkerRenderer,
                pngWriter,
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter(),
                renderDataCacheStore,
                subcommand
        );
    }

    public MapCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            UserMarkerRenderer userMarkerRenderer,
            PngWriter pngWriter,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualOreOverlayPainter actualOreOverlayPainter,
            RenderDataCacheStore renderDataCacheStore,
            String subcommand
    ) {
        this.out = out;
        this.pngWriter = pngWriter;
        this.renderActualOreMapUseCase = new RenderActualOreMapUseCase(
                reader, metadataReader, homeStore, markerStore, renderer, userMarkerRenderer,
                actualBlockMapScanner, actualOreOverlayPainter,
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new cartographer.application.OreChunkPositionPlanner(),
                new cartographer.save.SaveSessionFactory(
                        new cartographer.save.SqliteSaveConnection(), reader, metadataReader),
                renderDataCacheStore
        );
        this.subcommand = subcommand;
    }

    public MapCommand(
            PrintStream out,
            PngWriter pngWriter,
            RenderActualOreMapUseCase renderActualOreMapUseCase,
            String subcommand
    ) {
        this.out = out;
        this.pngWriter = pngWriter;
        this.renderActualOreMapUseCase = renderActualOreMapUseCase;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
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
                                ).orElse("")
                        )
                );

        ActualOreRequest actualOreRequest =
                actualOreRequest(
                        args
                );

        Path output =
                Path.of(
                        requiredOutput(
                                args
                        )
                );

        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                savePath,
                radius,
                scale,
                style,
                options.layers(),
                actualOreRequest.match(),
                actualOreRequest.yFilter(),
                center(args)
        );
        RenderActualOreMapResult result = renderActualOreMapUseCase.execute(request);

        ProgressReporter progress = new ProgressReporter(out);

        progress.start(
                "Writing PNG"
        );

        pngWriter.write(
                result.image(),
                output
        );

        progress.done(
                "PNG written"
        );

        printReport(
                output,
                result.renderReport(),
                result.userMarkersDrawn(),
                options,
                result.mapChunkDiagnostics(),
                result.chunkDiagnostics(),
                result.mapRegionDiagnostics(),
                result.surface(),
                result.environmentOverlay(),
                result.geologyOverlay(),
                result.actualOreMap().orElse(null),
                result.renderDataCacheReport()
        );
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
            MapRenderReport renderReport,
            int userMarkersDrawn,
            RenderOptions options,
            ReadDiagnostics mapChunkDiagnostics,
            ReadDiagnostics chunkDiagnostics,
            ReadDiagnostics mapRegionDiagnostics,
            SurfaceDiagnosticsSummary surface,
            OverlayRenderReport environmentOverlay,
            OverlayRenderReport geologyOverlay,
            ActualBlockMap actualOreMap,
            cartographer.application.RenderDataCacheReport cacheReport
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
                        + renderReport.width()
                        + "x"
                        + renderReport.height()
        );

        out.println(
                "Style: "
                        + renderReport.style()
        );

        out.println(
                "Layers: "
                        + renderReport.layers()
        );

        out.println(
                "Tiles drawn: "
                        + renderReport.tilesDrawn()
        );

        out.println(
                "System markers: "
                        + renderReport.markerCount()
        );

        out.println(
                "User markers: "
                        + userMarkersDrawn
        );

        out.println("Render-data cache: " + (cacheReport.enabled() ? "enabled" : "disabled"));
        printCacheStats("Terrain cache", cacheReport.terrain());
        printCacheStats("Surface cache", cacheReport.surface());
        for (String note : cacheReport.notes()) {
            out.println("Render-data cache note: " + note);
        }

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

        boolean surfaceDataRequired = options.layers().contains(RenderLayer.SURFACE)
                || options.layers().contains(RenderLayer.SOIL_FERTILITY);

        if (surfaceDataRequired) {

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

        if (actualOreMap != null) {
            out.println(
                    "Actual ore overlay: "
                            + actualOreMap.match()
            );

            out.println(
                    "Actual ore Y filter: "
                            + actualOreMap.yFilter()
                            .description()
            );

            out.println(
                    "Actual ore matching blocks: "
                            + actualOreMap.matchingBlocks()
            );

            out.println(
                    "Actual ore hit columns: "
                            + actualOreMap.hitColumns()
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

    private void printCacheStats(
            String name,
            cartographer.application.RenderDataCacheReport.ArtifactStats stats
    ) {
        out.println(name + ": requested=" + stats.requested()
                + " hit=" + stats.hits()
                + " miss=" + stats.misses()
                + " corrupt=" + stats.corruptOrIncompatible()
                + " source=" + stats.sourceLoaded()
                + " published=" + stats.published()
                + " skipped-incomplete=" + stats.skippedIncompleteForPublish());
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

    private void printFailureReasons(
            String title,
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                title + ":"
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
            SurfaceDiagnosticsSummary result
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

    private ActualOreRequest actualOreRequest(
            String[] args
    ) {
        Optional<String> match =
                option(
                        args,
                        "--actual-ore"
                );

        if (match.isPresent()
                && match.get().isBlank()) {

            throw new CommandException(
                    "--actual-ore must not be blank"
            );
        }

        Integer yMin =
                optionalIntegerOption(
                        args,
                        "--actual-y-min"
                );

        Integer yMax =
                optionalIntegerOption(
                        args,
                        "--actual-y-max"
                );

        if (match.isEmpty()
                && (yMin != null
                || yMax != null)) {
            throw new CommandException(
                    "--actual-y-min and --actual-y-max require --actual-ore"
            );
        }

        if (yMin != null
                && yMax != null
                && yMin > yMax) {
            throw new CommandException(
                    "--actual-y-min must not be greater than --actual-y-max"
            );
        }

        return new ActualOreRequest(
                match,
                new ActualBlockYFilter(
                        yMin,
                        yMax
                )
        );
    }

    private Integer optionalIntegerOption(
            String[] args,
            String optionName
    ) {
        Optional<String> value =
                option(
                        args,
                        optionName
                );

        if (value.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(
                    value.orElseThrow()
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value.orElse("")
            );
        }
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

    private static int getValue(
            String optionName,
            String option
    ) {
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

    private String requiredOutput(
            String[] args
    ) {
        return option(
                args,
                "--out"
        ).orElseThrow(
                () ->
                        new CommandException(
                                "Missing option: --out"
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

    private record ActualOreRequest(
            Optional<String> match,
            ActualBlockYFilter yFilter
    ) {
    }
}
