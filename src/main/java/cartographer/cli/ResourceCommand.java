package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
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
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceCandidate;
import cartographer.resource.ResourceHotspot;
import cartographer.resource.ResourceOverlayCell;
import cartographer.resource.ResourceSummary;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
        this.out =
                out;

        this.reader =
                reader;

        this.analyzer =
                analyzer;

        this.metadataReader =
                metadataReader;

        this.homeStore =
                homeStore;

        this.mapRenderer =
                mapRenderer;

        this.overlayRenderer =
                overlayRenderer;

        this.pngWriter =
                pngWriter;

        this.subcommand =
                subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        return switch (subcommand) {
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

            default ->
                    throw new CommandException(
                            "Unknown resource subcommand: "
                                    + subcommand
                    );
        };
    }

    private int list(
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
                                    "  " + key
                            )
            );
        }

        return 0;
    }

    private int inspect(
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
            return 0;
        }

        ResourceSummary summary =
                analyzer.summarize(
                                loaded.regions(),
                                selected.get(),
                                10
                        )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Resource map disappeared during inspection: "
                                                        + selected.get()
                                        )
                        );

        printSummary(
                summary
        );

        return 0;
    }

    private int search(
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
                        1,
                        MAX_TOP
                );

        int separation =
                intOption(
                        args,
                        "--separation",
                        DEFAULT_HOTSPOT_SEPARATION,
                        1,
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
            return 0;
        }

        List<ResourceHotspot> hotspots =
                analyzer.hotspots(
                        loaded.regions(),
                        selected.get(),
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
                        + selected.get()
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

            return 0;
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

        return 0;
    }

    private int render(
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
                        1,
                        8192
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        1,
                        1,
                        16
                );

        double minimumSignal =
                doubleOption(
                        args,
                        "--min-signal",
                        DEFAULT_MINIMUM_SIGNAL,
                        0.0,
                        1.0
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
            return 0;
        }

        String resourceKey =
                selected.get();

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
                        Optional.empty(),
                        progress
                );

        WorldPosition center =
                center(
                        args
                )
                        .orElse(
                                player
                        );

        Optional<HomeLocation> home =
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
                new RenderOptions(
                        radius,
                        scale,
                        style,
                        EnumSet.of(
                                RenderLayer.TERRAIN
                        )
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

        return 0;
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
                matches.get(
                        0
                )
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
            int minimum,
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
                            value.get()
                    );

            if (parsed < minimum
                    || parsed > maximum) {

                throw new CommandException(
                        optionName
                                + " must be between "
                                + minimum
                                + " and "
                                + maximum
                );
            }

            return parsed;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value.get()
            );
        }
    }

    private double doubleOption(
            String[] args,
            String optionName,
            double defaultValue,
            double minimum,
            double maximum
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
            double parsed =
                    Double.parseDouble(
                            value.get()
                    );

            if (!Double.isFinite(
                    parsed
            )
                    || parsed < minimum
                    || parsed > maximum) {

                throw new CommandException(
                        optionName
                                + " must be between "
                                + minimum
                                + " and "
                                + maximum
                );
            }

            return parsed;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value.get()
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
                                x.get(),
                                "--center-x"
                        ),
                        0.0,
                        parseDouble(
                                z.get(),
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

        WorldPosition absolute =
                metadata.toAbsolute(
                        new DisplayPosition(
                                displayHome.get().x(),
                                0.0,
                                displayHome.get().z()
                        )
                );

        return Optional.of(
                new HomeLocation(
                        absolute.x(),
                        absolute.z()
                )
        );
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
}