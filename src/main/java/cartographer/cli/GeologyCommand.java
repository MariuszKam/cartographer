package cartographer.cli;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.GeologyReport;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.geology.RockStrataAnalyzer;
import cartographer.geology.RockStrataSummary;
import cartographer.geology.RockStratumSummary;
import cartographer.geology.crosssection.GeologyCrossSection;
import cartographer.geology.crosssection.GeologyCrossSectionAnalyzer;
import cartographer.geology.crosssection.GeologySectionColumn;
import cartographer.geology.crosssection.GeologySectionRun;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.render.GeologyCrossSectionRenderer;
import cartographer.render.GeologySectionMarker;
import cartographer.render.PngWriter;
import cartographer.render.RenderStyle;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.application.PrepareMapDataRequest;
import cartographer.application.PrepareMapDataUseCase;
import cartographer.application.PreparedMapData;
import cartographer.application.SurfaceDataRequirement;
import cartographer.scanner.SurfaceMapScanResult;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GeologyCommand implements Command {

    private static final int DEFAULT_RADIUS =
            512;

    private static final int MAX_RADIUS =
            8192;

    private static final int MAX_SECTION_SPAN =
            4096;

    private static final int DEFAULT_SECTION_RADIUS =
            500;

    private static final int DEFAULT_HORIZONTAL_SCALE =
            1;

    private static final int DEFAULT_VERTICAL_SCALE =
            2;

    private static final int MAX_SECTION_SCALE =
            8;

    private static final Path DEFAULT_SECTION_OUTPUT =
            Path.of(
                    "output",
                    "geology-section.png"
            );

    private final PrintStream out;
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final PrepareMapDataUseCase mapDataUseCase;
    private final GeologyAnalyzer geologyAnalyzer;
    private final GeologyCrossSectionAnalyzer crossSectionAnalyzer;
    private final GeologyCrossSectionRenderer crossSectionRenderer;
    private final PngWriter pngWriter;
    private final String subcommand;

    public GeologyCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            GeologyAnalyzer geologyAnalyzer,
            String subcommand
    ) {
        this(
                out,
                reader,
                sessionFactory,
                geologyAnalyzer,
                new GeologyCrossSectionAnalyzer(),
                new GeologyCrossSectionRenderer(),
                new PngWriter(),
                subcommand
        );
    }

    public GeologyCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            GeologyAnalyzer geologyAnalyzer,
            GeologyCrossSectionAnalyzer crossSectionAnalyzer,
            GeologyCrossSectionRenderer crossSectionRenderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.sessionFactory = sessionFactory;
        this.mapDataUseCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory
        );
        this.geologyAnalyzer = geologyAnalyzer;
        this.crossSectionAnalyzer = crossSectionAnalyzer;
        this.crossSectionRenderer = crossSectionRenderer;
        this.pngWriter = pngWriter;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        switch (subcommand) {
            case "surface" ->
                    runSurface(
                            args
                    );

            case "strata" ->
                    runStrata(
                            args
                    );

            case "section" ->
                    runSection(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown geology subcommand: "
                                    + subcommand
                    );
        }
    }

    private void runSurface(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: geology surface <save.vcdbs> "
                            + "--radius <blocks> "
                            + "[--center-x <x> --center-z <z>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        int radius =
                radiusOption(
                        args
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        PreparedMapData loaded = mapDataUseCase.execute(
                new PrepareMapDataRequest(
                        savePath,
                        radius,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(),
                        center(args),
                        SurfaceDataRequirement.ANALYSIS
                ),
                progress
        );
        SurfaceMapScanResult surface =
                loaded.surface().requireAnalysis();
        ReadDiagnostics diagnostics =
                loaded.chunkDiagnostics();

        GeologyReport report =
                geologyAnalyzer.analyze(
                surface
                );

        out.println(
                "GEOLOGY SURFACE"
        );

        out.println(
                "Chunks parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Chunks skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Chunks failed: "
                        + diagnostics.failed()
        );

        out.println(
                "Surface samples: "
                        + report.samples()
        );

        out.println(
                "Geological samples: "
                        + report.geologicalSamples()
        );

        out.println(
                "Unknown samples: "
                        + report.unknownSamples()
        );

        printMap(
                "Rock families",
                report.rockFamilies()
        );

        printMap(
                "Material types",
                report.materialTypes()
        );

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private void runStrata(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: geology strata <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        List<ServerMapRegion> regions;

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            regions =
                    reader.readMapRegions(
                            session,
                            diagnostics,
                            progress
                    );
        }

        RockStrataAnalyzer strataAnalyzer =
                new RockStrataAnalyzer();

        GeologicProvinceInterpreter provinceInterpreter =
                new GeologicProvinceInterpreter();

        out.println(
                "GEOLOGY STRATA"
        );

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

        for (ServerMapRegion region : regions) {
            RockStrataSummary strataSummary =
                    strataAnalyzer.summarize(
                            region
                    );

            Optional<GeologicProvinceSummary> province =
                    provinceInterpreter.summarize(
                            region
                    );

            if (strataSummary.strata().isEmpty()
                    && province.isEmpty()) {

                continue;
            }

            out.println();

            out.println(
                    "Region "
                            + region.coordinate().x()
                            + ","
                            + region.coordinate().z()
            );

            out.println(
                    "  RockStrata maps: "
                            + strataSummary.strata().size()
            );

            for (RockStratumSummary stratum :
                    strataSummary.strata()) {

                out.println(
                        "    stratum "
                                + stratum.index()
                                + ": size="
                                + stratum.size()
                                + "x"
                                + stratum.size()
                                + " padding="
                                + stratum.topLeftPadding()
                                + "/"
                                + stratum.bottomRightPadding()
                                + " innerSize="
                                + stratum.innerSize()
                                + " samples="
                                + stratum.samples()
                                + " minRawValue="
                                + stratum.minRawValue()
                                + " maxRawValue="
                                + stratum.maxRawValue()
                                + " distinct="
                                + stratum.distinctCount()
                                + " dominantRawValues="
                                + stratum.dominantRawValues()
                );
            }

            if (province.isPresent()) {
                GeologicProvinceSummary summary =
                        province.get();

                out.println(
                        "  GeologicProvinceMap: samples="
                                + summary.samples()
                                + " distinct="
                                + summary.distinctCount()
                                + " dominantRawIds="
                                + summary.dominantIds()
                );

            } else {
                out.println(
                        "  GeologicProvinceMap: missing"
                );
            }
        }

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private void runSection(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: geology section <save.vcdbs> "
                            + "[--axis east-west|north-south] "
                            + "[--radius <blocks>] "
                            + "[--from-x <world-x> "
                            + "--from-z <world-z> "
                            + "--to-x <world-x> "
                            + "--to-z <world-z>] "
                            + "[--out <section.png>] "
                            + "[--horizontal-scale <1..8>] "
                            + "[--vertical-scale <1..8>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player;
        SectionRequest request;
        long span;
        int horizontalScale;
        int verticalScale;
        Path output;
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();
        List<ParsedChunk> chunks;
        Map<Integer, BlockInfo> registry;

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            player =
                    reader.readPlayerPosition(
                            session,
                            progress
                    );

            request =
                    manualSectionRequested(
                            args
                    )
                            ? manualSection(
                            args
                    )
                            : playerCenteredSection(
                            args,
                            player
                    );

            span =
                    sectionSpan(
                            request.fromX(),
                            request.fromZ(),
                            request.toX(),
                            request.toZ()
                    );

            if (span > MAX_SECTION_SPAN) {
                throw new CommandException(
                        "Geology section span must not exceed "
                                + MAX_SECTION_SPAN
                                + " blocks"
                );
            }

            horizontalScale =
                    scaleOption(
                            args,
                            "--horizontal-scale",
                            DEFAULT_HORIZONTAL_SCALE
                    );

            verticalScale =
                    scaleOption(
                            args,
                            "--vertical-scale",
                            DEFAULT_VERTICAL_SCALE
                    );

            output =
                    option(
                            args,
                            "--out"
                    )
                            .map(
                                    Path::of
                            )
                            .orElse(
                                    DEFAULT_SECTION_OUTPUT
                            );

            WorldPosition center =
                    new WorldPosition(
                            midpoint(
                                    request.fromX(),
                                    request.toX()
                            ),
                            0.0,
                            midpoint(
                                    request.fromZ(),
                                    request.toZ()
                            )
                    );

            int readRadius =
                    sectionReadRadius(
                            span
                    );

            chunks =
                    reader.readChunksAround(
                            session,
                            center,
                            readRadius,
                            diagnostics,
                            progress
                    );

            registry =
                    session.snapshot()
                            .blockRegistry();
        }

        progress.start(
                "Analyzing geology cross-section"
        );

        GeologyCrossSection section =
                crossSectionAnalyzer.analyze(
                        chunks,
                        registry,
                        request.fromX(),
                        request.fromZ(),
                        request.toX(),
                        request.toZ()
                );

        GeologySectionMarker playerMarker =
                playerMarker(
                        section,
                        player
                );

        progress.done(
                "Geology cross-section analyzed"
        );

        progress.start(
                "Rendering geology cross-section"
        );

        BufferedImage image;

        try {
            image =
                    crossSectionRenderer.render(
                            section,
                            horizontalScale,
                            verticalScale,
                            playerMarker
                    );

        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new CommandException(
                    "Cannot render geology section: "
                            + exception.getMessage(),
                    exception
            );
        }

        progress.done(
                "Geology cross-section rendered"
        );

        progress.start(
                "Writing PNG"
        );

        pngWriter.write(
                image,
                output
        );

        progress.done(
                "PNG written"
        );

        SectionStats stats =
                sectionStats(
                        section
                );

        out.println(
                "GEOLOGY SECTION"
        );

        out.println(
                "Mode: "
                        + request.mode()
        );

        out.printf(
                Locale.ROOT,
                "Player world: %.3f,%.3f,%.3f%n",
                player.x(),
                player.y(),
                player.z()
        );

        if (request.playerCentered()) {
            out.println(
                    "Axis: "
                            + request.axis()
            );

            out.println(
                    "Radius: "
                            + request.radius()
                            + " blocks"
            );
        }

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "From world: "
                        + request.fromX()
                        + ","
                        + request.fromZ()
        );

        out.println(
                "To world: "
                        + request.toX()
                        + ","
                        + request.toZ()
        );

        out.println(
                "Columns: "
                        + section.columns().size()
        );

        if (section.minYInclusive()
                == section.maxYExclusive()) {

            out.println(
                    "Y range: unavailable"
            );

        } else {
            out.println(
                    "Y range: "
                            + section.minYInclusive()
                            + ".."
                            + (section.maxYExclusive() - 1)
            );
        }

        out.println(
                "Observed block samples: "
                        + stats.observedSamples()
        );

        out.println(
                "Unavailable block samples: "
                        + stats.unavailableSamples()
        );

        out.println(
                "Ore block samples: "
                        + stats.oreSamples()
        );

        out.println(
                "Image: "
                        + image.getWidth()
                        + "x"
                        + image.getHeight()
        );

        out.println(
                "Horizontal scale: "
                        + horizontalScale
        );

        out.println(
                "Vertical scale: "
                        + verticalScale
        );

        if (playerMarker == null) {
            out.println(
                    "Player marker: not on section line"
            );

        } else {
            out.println(
                    "Player marker: column "
                            + playerMarker.columnIndex()
                            + " at Y "
                            + playerMarker.worldY()
            );
        }

        out.println(
                "Chunks parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Chunks skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Chunks failed: "
                        + diagnostics.failed()
        );

        printFailureReasons(
                diagnostics
        );

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private SectionStats sectionStats(
            GeologyCrossSection section
    ) {
        long observed =
                0L;

        long unavailable =
                0L;

        long ore =
                0L;

        for (GeologySectionColumn column :
                section.columns()) {

            for (GeologySectionRun run :
                    column.runs()) {

                long samples =
                        (long) run.maxYExclusive()
                                - run.minYInclusive();

                if (!run.observed()) {
                    unavailable +=
                            samples;

                    continue;
                }

                observed +=
                        samples;

                if (isOreCode(
                        run.blockCode()
                )) {
                    ore +=
                            samples;
                }
            }
        }

        return new SectionStats(
                observed,
                unavailable,
                ore
        );
    }

    private boolean isOreCode(
            String code
    ) {
        if (code == null) {
            return false;
        }

        String normalized =
                code.toLowerCase(
                        Locale.ROOT
                );

        return normalized.startsWith(
                "ore-"
        )
                || normalized.contains(
                ":ore-"
        )
                || normalized.contains(
                "-ore-"
        );
    }

    private long sectionSpan(
            int fromX,
            int fromZ,
            int toX,
            int toZ
    ) {
        long spanX =
                Math.abs(
                        (long) toX
                                - fromX
                );

        long spanZ =
                Math.abs(
                        (long) toZ
                                - fromZ
                );

        return Math.max(
                spanX,
                spanZ
        );
    }

    private boolean manualSectionRequested(
            String[] args
    ) {
        return option(
                args,
                "--from-x"
        ).isPresent()
                || option(
                args,
                "--from-z"
        ).isPresent()
                || option(
                args,
                "--to-x"
        ).isPresent()
                || option(
                args,
                "--to-z"
        ).isPresent();
    }

    private SectionRequest manualSection(
            String[] args
    ) {
        if (option(
                args,
                "--axis"
        ).isPresent()
                || option(
                args,
                "--radius"
        ).isPresent()) {

            throw new CommandException(
                    "--axis and --radius cannot be combined with manual --from/--to coordinates"
            );
        }

        return new SectionRequest(
                requiredIntOption(
                        args,
                        "--from-x"
                ),
                requiredIntOption(
                        args,
                        "--from-z"
                ),
                requiredIntOption(
                        args,
                        "--to-x"
                ),
                requiredIntOption(
                        args,
                        "--to-z"
                ),
                null,
                0,
                false
        );
    }

    private SectionRequest playerCenteredSection(
            String[] args,
            WorldPosition player
    ) {
        int playerX =
                (int) Math.round(
                        player.x()
                );

        int playerZ =
                (int) Math.round(
                        player.z()
                );

        int radius =
                sectionRadiusOption(
                        args
                );

        SectionAxis axis =
                sectionAxisOption(
                        args
                );

        return switch (axis) {
            case EAST_WEST ->
                    new SectionRequest(
                            playerX - radius,
                            playerZ,
                            playerX + radius,
                            playerZ,
                            axis,
                            radius,
                            true
                    );

            case NORTH_SOUTH ->
                    new SectionRequest(
                            playerX,
                            playerZ - radius,
                            playerX,
                            playerZ + radius,
                            axis,
                            radius,
                            true
                    );
        };
    }

    private int sectionRadiusOption(
            String[] args
    ) {
        Optional<String> raw =
                option(
                        args,
                        "--radius"
                );

        if (raw.isEmpty()) {
            return DEFAULT_SECTION_RADIUS;
        }

        try {
            int value =
                    Integer.parseInt(
                            raw.get()
                    );

            if (value <= 0) {
                throw new CommandException(
                        "--radius must be positive"
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid --radius: "
                            + raw.get()
            );
        }
    }

    private SectionAxis sectionAxisOption(
            String[] args
    ) {
        Optional<String> raw =
                option(
                        args,
                        "--axis"
                );

        if (raw.isEmpty()) {
            return SectionAxis.EAST_WEST;
        }

        return switch (raw.get()
                .toLowerCase(
                        Locale.ROOT
                )) {
            case "east-west", "ew", "x" ->
                    SectionAxis.EAST_WEST;

            case "north-south", "ns", "z" ->
                    SectionAxis.NORTH_SOUTH;

            default ->
                    throw new CommandException(
                            "Invalid --axis: "
                                    + raw.get()
                                    + " (expected east-west or north-south)"
                    );
        };
    }

    private int sectionReadRadius(
            long span
    ) {
        long radius =
                Math.max(
                        ChunkCoordinate.SIZE_BLOCKS,
                        (span + 1L) / 2L
                                + ChunkCoordinate.SIZE_BLOCKS
                );

        if (radius > MAX_RADIUS) {
            throw new CommandException(
                    "Required geology read radius exceeds "
                            + MAX_RADIUS
                            + " blocks"
            );
        }

        return Math.toIntExact(
                radius
        );
    }

    private double midpoint(
            int first,
            int second
    ) {
        return first
                + (
                second
                        - (double) first
        )
                / 2.0;
    }

    private GeologySectionMarker playerMarker(
            GeologyCrossSection section,
            WorldPosition player
    ) {
        int playerX =
                (int) Math.round(
                        player.x()
                );

        int playerZ =
                (int) Math.round(
                        player.z()
                );

        int playerY =
                (int) Math.round(
                        player.y()
                );

        for (GeologySectionColumn column :
                section.columns()) {

            if (column.worldX() == playerX
                    && column.worldZ() == playerZ) {

                return new GeologySectionMarker(
                        "PLAYER",
                        column.index(),
                        playerY
                );
            }
        }

        return null;
    }

    private int requiredIntOption(
            String[] args,
            String optionName
    ) {
        String value =
                option(
                        args,
                        optionName
                )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Missing required option: "
                                                        + optionName
                                        )
                        );

        try {
            return Integer.parseInt(
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

    private int scaleOption(
            String[] args,
            String optionName,
            int defaultValue
    ) {
        Optional<String> raw =
                option(
                        args,
                        optionName
                );

        if (raw.isEmpty()) {
            return defaultValue;
        }

        try {
            int value =
                    Integer.parseInt(
                            raw.get()
                    );

            if (value < 1
                    || value > MAX_SECTION_SCALE) {

                throw new CommandException(
                        optionName
                                + " must be between 1 and "
                                + MAX_SECTION_SCALE
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + raw.get()
            );
        }
    }

    private void printMap(
            String title,
            Map<String, Integer> values
    ) {
        out.println(
                title + ":"
        );

        values.entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByValue(
                                Comparator.reverseOrder()
                        )
                )
                .forEach(
                        entry ->
                                out.printf(
                                        "  %s: %d%n",
                                        entry.getKey(),
                                        entry.getValue()
                                )
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
                                        "  " + line
                                )
                );
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

    private int radiusOption(
            String[] args
    ) {
        Optional<String> option =
                option(
                        args,
                        "--radius"
                );

        if (option.isEmpty()) {
            return DEFAULT_RADIUS;
        }

        try {
            int value =
                    Integer.parseInt(
                            option.get()
                    );

            if (value <= 0
                    || value > MAX_RADIUS) {

                throw new CommandException(
                        "--radius must be between 1 and "
                                + MAX_RADIUS
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid --radius: "
                            + option.get()
            );
        }
    }

    private Optional<String> option(
            String[] args,
            String optionName
    ) {
        for (int index = 1;
             index < args.length;
             index++) {

            if (optionName.equals(
                    args[index]
            )) {
                if (index == args.length - 1) {
                    throw new CommandException(
                            "Missing value for option: "
                                    + optionName
                    );
                }

                return Optional.of(
                        args[index + 1]
                );
            }
        }

        return Optional.empty();
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

    private record SectionStats(
            long observedSamples,
            long unavailableSamples,
            long oreSamples
    ) {
    }

    private enum SectionAxis {
        EAST_WEST,
        NORTH_SOUTH
    }

    private record SectionRequest(
            int fromX,
            int fromZ,
            int toX,
            int toZ,
            SectionAxis axis,
            int radius,
            boolean playerCentered
    ) {

        private String mode() {
            return playerCentered
                    ? "PLAYER_CENTERED"
                    : "MANUAL";
        }
    }
}
