package cartographer.cli;

import cartographer.analysis.BlockMatch;
import cartographer.analysis.BlockScanResult;
import cartographer.analysis.BlockScanner;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.application.PrepareMapDataRequest;
import cartographer.application.PrepareMapDataUseCase;
import cartographer.application.PreparedMapData;
import cartographer.application.SurfaceDataRequirement;
import cartographer.model.WorldPosition;
import cartographer.render.ActualBlockMapRenderer;
import cartographer.render.PngWriter;
import cartographer.render.RenderStyle;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.SurfaceMapScanResult;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ScanCommand implements Command {
    private static final int DEFAULT_BLOCK_MAP_RADIUS =
            256;

    private static final int DEFAULT_BLOCK_MAP_SCALE =
            2;

    private static final int MAX_BLOCK_MAP_RADIUS =
            1024;

    private static final int MAX_BLOCK_MAP_SCALE =
            8;

    private static final Path DEFAULT_BLOCK_MAP_OUTPUT =
            Path.of(
                    "output",
                    "actual-block-map.png"
            );

    private static final Path DEFAULT_BLOCK_MAP_OUTPUT_DIRECTORY =
            Path.of(
                    "output",
                    "actual-block-map-bands"
            );

    private final PrintStream out;
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final PrepareMapDataUseCase mapDataUseCase;
    private final BlockScanner blockScanner;
    private final ActualBlockMapScanner actualBlockMapScanner;
    private final ActualBlockMapRenderer actualBlockMapRenderer;
    private final PngWriter pngWriter;
    private final String subcommand;

    public ScanCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            BlockScanner blockScanner,
            String subcommand
    ) {
        this(
                out,
                reader,
                sessionFactory,
                blockScanner,
                new ActualBlockMapScanner(),
                new ActualBlockMapRenderer(),
                new PngWriter(),
                subcommand
        );
    }

    public ScanCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            BlockScanner blockScanner,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualBlockMapRenderer actualBlockMapRenderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.sessionFactory = sessionFactory;
        this.mapDataUseCase = new PrepareMapDataUseCase(
                reader,
                new WorldMetadataReader()
        );
        this.blockScanner = blockScanner;
        this.actualBlockMapScanner = actualBlockMapScanner;
        this.actualBlockMapRenderer = actualBlockMapRenderer;
        this.pngWriter = pngWriter;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        if ("blocks".equals(
                subcommand
        )) {
            scanBlocks(
                    args
            );

            return;
        }

        if ("blocks-map".equals(
                subcommand
        )) {
            runBlocksMap(
                    args
            );

            return;
        }

        if (!"surface".equals(
                subcommand
        )) {
            throw new CommandException(
                    "Unknown scan subcommand: "
                            + subcommand
            );
        }

        if (args.length < 1) {
            throw new CommandException(
                    "Usage: scan surface <save.vcdbs> "
                            + "--radius <blocks> "
                            + "[--include-foliage]"
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
                        256
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
                        Optional.empty(),
                        SurfaceDataRequirement.ANALYSIS,
                        !includeFoliage(args)
                ),
                progress
        );
        SurfaceMapScanResult result =
                loaded.surface().requireAnalysis();
        ReadDiagnostics diagnostics =
                loaded.chunkDiagnostics();
        Map<Integer, BlockInfo> registry =
                loaded.registry();

        out.println(
                "SURFACE"
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

        printFailureReasons(
                diagnostics
        );

        printLiquidFailureReasons(
                diagnostics
        );

        out.println(
                "Registry blocks: "
                        + registry.size()
        );

        out.println(
                "Columns scanned: "
                        + result.columnsScanned()
        );

        out.println(
                "Empty columns: "
                        + result.emptyColumns()
        );

        out.println(
                "Liquid unavailable columns: "
                        + result.liquidUnavailableColumns()
        );

        out.println(
                "Surface blocks: "
                        + resolvedCellCount(result)
        );

        out.println(
                "Water columns: "
                        + result.waterColumns()
        );

        out.println(
                "Unknown surface blocks: "
                        + result.unknownSurfaceBlocks()
        );

        printTopUnknownSurfaceBlockCodes(
                result
        );

        out.println(
                "Distinct surface block codes: "
                        + result.distinctSurfaceBlockCodes(
                        20
                )
        );

        int[] samples = {0};
        result.map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (samples[0]++ < 20) printSurfaceCell(x, y, z, blockId, registry);
        });

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private void runBlocksMap(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: scan blocks-map <save.vcdbs> "
                            + "--match <text> "
                            + "[--radius <blocks>] "
                            + "[--center-x <x> --center-z <z>] "
                            + "[--y-min <y>] "
                            + "[--y-max <y>] "
                            + "[--split-y <band-size>] "
                            + "[--out-dir <directory>] "
                            + "[--scale <1..8>] "
                            + "[--out <image.png>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String match =
                option(
                        args,
                        "--match"
                ).orElseThrow(
                        () ->
                                new CommandException(
                                        "Missing required option: --match"
                                )
                );

        int radius =
                intOption(
                        args,
                        "--radius",
                        DEFAULT_BLOCK_MAP_RADIUS,
                        MAX_BLOCK_MAP_RADIUS
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        DEFAULT_BLOCK_MAP_SCALE,
                        MAX_BLOCK_MAP_SCALE
                );

        ActualBlockYFilter yFilter =
                yFilterOption(
                        args
                );

        Optional<Integer> splitY =
                splitYOption(
                        args
                );

        if (splitY.isPresent()
                && option(
                args,
                "--out"
        ).isPresent()) {

            throw new CommandException(
                    "--out cannot be used with --split-y; use --out-dir"
            );
        }

        if (splitY.isEmpty()
                && option(
                args,
                "--out-dir"
        ).isPresent()) {

            throw new CommandException(
                    "--out-dir requires --split-y"
            );
        }

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition center;
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();
        List<ParsedChunk> chunks;
        Map<Integer, BlockInfo> registry;

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            center =
                    center(
                            args
                    ).orElseGet(
                            () ->
                                    reader.readPlayerPosition(
                                            session,
                                            progress
                                    )
                    );

            chunks =
                    reader.readChunksAround(
                            session,
                            center,
                            radius,
                            diagnostics,
                            progress
                    );

            registry =
                    session.snapshot()
                            .blockRegistry();
        }

        int centerX =
                (int) Math.round(
                        center.x()
                );

        int centerZ =
                (int) Math.round(
                        center.z()
                );

        if (splitY.isPresent()) {
            runBlocksMapBands(
                    args,
                    match,
                    radius,
                    scale,
                    centerX,
                    centerZ,
                    chunks,
                    registry,
                    yFilter,
                    splitY.orElseThrow(),
                    diagnostics,
                    progress
            );

            return;
        }

        progress.start(
                "Scanning actual matching blocks"
        );

        ActualBlockMap map =
                actualBlockMapScanner.scan(
                        chunks,
                        registry,
                        centerX,
                        centerZ,
                        radius,
                        match,
                        yFilter
                );

        progress.done(
                "Actual matching blocks scanned"
        );

        progress.start(
                "Rendering actual block map"
        );

        BufferedImage image =
                actualBlockMapRenderer.render(
                        map,
                        scale
                );

        progress.done(
                "Actual block map rendered"
        );

        progress.start(
                "Writing PNG"
        );

        pngWriter.write(
                image,
                blockMapOutput(
                        args
                )
        );

        progress.done(
                "PNG written"
        );

        out.println(
                "ACTUAL BLOCK MAP"
        );

        out.println(
                "Match: "
                        + match
        );

        out.println(
                "Output: "
                        + blockMapOutput(
                        args
                )
        );

        out.println(
                "Center: "
                        + centerX
                        + ","
                        + centerZ
        );

        out.println(
                "Radius: "
                        + radius
        );

        out.println(
                "Scale: "
                        + scale
        );

        out.println(
                "Y filter: "
                        + yFilter.description()
        );

        out.println(
                "Matching blocks: "
                        + map.matchingBlocks()
        );

        out.println(
                "Hit columns: "
                        + map.hitColumns()
        );

        if (map.cells().isEmpty()) {
            out.println(
                    "Y range: none"
            );

        } else {
            out.println(
                    "Y range: "
                            + map.minMatchedY()
                            + ".."
                            + map.maxMatchedY()
            );
        }

        out.println(
                "Image: "
                        + image.getWidth()
                        + "x"
                        + image.getHeight()
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

        printFailureReasons(
                diagnostics
        );

        printLiquidFailureReasons(
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

    private void runBlocksMapBands(
            String[] args,
            String match,
            int radius,
            int scale,
            int centerX,
            int centerZ,
            List<ParsedChunk> chunks,
            Map<Integer, BlockInfo> registry,
            ActualBlockYFilter outerFilter,
            int bandSize,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        VerticalRange loadedRange =
                loadedVerticalRange(
                        chunks
                );

        int minY =
                outerFilter.minInclusive() == null
                        ? loadedRange.minY()
                        : outerFilter.minInclusive();

        int maxY =
                outerFilter.maxInclusive() == null
                        ? loadedRange.maxY()
                        : outerFilter.maxInclusive();

        if (minY > maxY) {
            throw new CommandException(
                    "Y band range is empty"
            );
        }

        Path outputDirectory =
                option(
                        args,
                        "--out-dir"
                )
                        .map(
                                Path::of
                        )
                        .orElse(
                                DEFAULT_BLOCK_MAP_OUTPUT_DIRECTORY
                        );

        out.println(
                "ACTUAL BLOCK MAP BANDS"
        );

        out.println(
                "Match: "
                        + match
        );

        out.println(
                "Output directory: "
                        + outputDirectory
        );

        out.println(
                "Center: "
                        + centerX
                        + ","
                        + centerZ
        );

        out.println(
                "Radius: "
                        + radius
        );

        out.println(
                "Scale: "
                        + scale
        );

        out.println(
                "Band size: "
                        + bandSize
        );

        out.println(
                "Y filter: "
                        + new ActualBlockYFilter(
                        minY,
                        maxY
                ).description()
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

        printFailureReasons(
                diagnostics
        );

        printLiquidFailureReasons(
                diagnostics
        );

        for (int bandMin = minY;
             bandMin <= maxY;
             bandMin = Math.addExact(
                     bandMin,
                     bandSize
             )) {

            int bandMax =
                    Math.min(
                            maxY,
                            Math.addExact(
                                    bandMin,
                                    bandSize - 1
                            )
                    );

            ActualBlockYFilter bandFilter =
                    new ActualBlockYFilter(
                            bandMin,
                            bandMax
                    );

            progress.start(
                    "Scanning actual matching blocks for Y "
                            + bandFilter.description()
            );

            ActualBlockMap map =
                    actualBlockMapScanner.scan(
                            chunks,
                            registry,
                            centerX,
                            centerZ,
                            radius,
                            match,
                            bandFilter
                    );

            progress.done(
                    "Actual matching blocks scanned"
            );

            progress.start(
                    "Rendering actual block map for Y "
                            + bandFilter.description()
            );

            BufferedImage image =
                    actualBlockMapRenderer.render(
                            map,
                            scale
                    );

            progress.done(
                    "Actual block map rendered"
            );

            Path output =
                    outputDirectory.resolve(
                            bandFilename(
                                    match,
                                    bandMin,
                                    bandMax
                            )
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

            out.println(
                    "Band "
                            + bandFilter.description()
                            + ": output="
                            + output
                            + " matchingBlocks="
                            + map.matchingBlocks()
                            + " hitColumns="
                            + map.hitColumns()
                            + " yRange="
                            + yRangeText(
                            map
                    )
                            + " image="
                            + image.getWidth()
                            + "x"
                            + image.getHeight()
            );

            if (bandMax == Integer.MAX_VALUE) {
                break;
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

    private Path blockMapOutput(
            String[] args
    ) {
        return option(
                args,
                "--out"
        )
                .map(
                        Path::of
                )
                .orElse(
                        DEFAULT_BLOCK_MAP_OUTPUT
                );
    }

    private VerticalRange loadedVerticalRange(
            List<ParsedChunk> chunks
    ) {
        int minY =
                Integer.MAX_VALUE;

        int maxY =
                Integer.MIN_VALUE;

        for (ParsedChunk chunk : chunks) {
            minY =
                    Math.min(
                            minY,
                            chunk.minY()
                    );

            maxY =
                    Math.max(
                            maxY,
                            Math.addExact(
                                    chunk.minY(),
                                    chunk.sizeY() - 1
                            )
                    );
        }

        if (minY == Integer.MAX_VALUE) {
            throw new CommandException(
                    "Cannot split Y bands without loaded chunks"
            );
        }

        return new VerticalRange(
                minY,
                maxY
        );
    }

    private String bandFilename(
            String match,
            int minY,
            int maxY
    ) {
        return safeFilenamePrefix(
                match
        )
                + "-y"
                + yToken(
                minY
        )
                + "-"
                + yToken(
                maxY
        )
                + ".png";
    }

    private String safeFilenamePrefix(
            String match
    ) {
        String normalized =
                match.toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9._-]+",
                                "-"
                        )
                        .replaceAll(
                                "^-+|-+$",
                                ""
                        );

        return normalized.isBlank()
                ? "blocks"
                : normalized;
    }

    private String yToken(
            int y
    ) {
        if (y < 0) {
            return "n"
                    + String.format(
                    Locale.ROOT,
                    "%03d",
                    Math.abs(
                            y
                    )
            );
        }

        return String.format(
                Locale.ROOT,
                "%03d",
                y
        );
    }

    private String yRangeText(
            ActualBlockMap map
    ) {
        if (map.cells()
                .isEmpty()) {
            return "none";
        }

        return map.minMatchedY()
                + ".."
                + map.maxMatchedY();
    }

    private void scanBlocks(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: scan blocks <save.vcdbs> "
                            + "--match <text> "
                            + "[--radius <blocks>] "
                            + "[--limit <n>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String match =
                option(
                        args,
                        "--match"
                ).orElseThrow(
                        () ->
                                new CommandException(
                                        "Missing option: --match"
                                )
                );

        int radius =
                intOption(
                        args,
                        "--radius",
                        256
                );

        int limit =
                intOption(
                        args,
                        "--limit",
                        100
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition center;
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();
        List<ParsedChunk> chunks;
        Map<Integer, BlockInfo> registry;

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            center =
                    center(
                            args
                    ).orElseGet(
                            () ->
                                    reader.readPlayerPosition(
                                            session,
                                            progress
                                    )
                    );

            chunks =
                    reader.readChunksAround(
                            session,
                            center,
                            radius,
                            diagnostics,
                            progress
                    );

            registry =
                    session.snapshot()
                            .blockRegistry();
        }

        BlockScanResult result =
                blockScanner.scan(
                        chunks,
                        registry,
                        match,
                        limit,
                        progress
                );

        out.println("BLOCK SCAN");
        out.println("Match: " + match);
        out.println("Chunks parsed: " + diagnostics.parsed());
        out.println("Chunks skipped: " + diagnostics.skipped());
        out.println("Chunks failed: " + diagnostics.failed());

        printFailureReasons(
                diagnostics
        );

        printLiquidFailureReasons(
                diagnostics
        );

        out.println("Blocks scanned: " + result.blocksScanned());
        out.println("Matches: " + result.matches().size());
        out.println("Truncated: " + result.truncated());

        result.matches()
                .forEach(
                        this::printBlockMatch
                );

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: " + note
                                )
                );
    }

    private void printSurfaceCell(int worldX, int y, int worldZ, int blockId,
                                  Map<Integer, BlockInfo> registry) {
        BlockInfo block = registry.get(blockId);
        if (block == null) block = BlockInfo.unknown(blockId);
        out.printf(
                "%d,%d,%d %s %s%n",
                worldX, y, worldZ, block.code(), block.materialType()
        );
    }

    private int resolvedCellCount(SurfaceMapScanResult result) {
        int[] count = {0};
        result.map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> count[0]++);
        return count[0];
    }

    private void printBlockMatch(
            BlockMatch match
    ) {
        out.printf(
                "%d,%d,%d %s %s%n",
                match.worldX(),
                match.y(),
                match.worldZ(),
                match.blockInfo().code(),
                match.blockInfo().materialType()
        );
    }

    private void printTopUnknownSurfaceBlockCodes(
            SurfaceMapScanResult result
    ) {
        if (result.unknownSurfaceBlocks() <= 0) {
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
                                        "  " + line
                                )
                );
    }

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue
    ) {
        return intOption(
                args,
                optionName,
                defaultValue,
                8192
        );
    }

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue,
            int maxValue
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
            int value =
                    Integer.parseInt(
                            option.get()
                    );

            if (value <= 0
                    || value > maxValue) {
                throw new CommandException(
                        optionName
                                + " must be between "
                                + 1
                                + " and "
                                + maxValue
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + option.get()
            );
        }
    }

    private ActualBlockYFilter yFilterOption(
            String[] args
    ) {
        Integer yMin =
                optionalIntegerOption(
                        args,
                        "--y-min"
                );

        Integer yMax =
                optionalIntegerOption(
                        args,
                        "--y-max"
                );

        if (yMin != null
                && yMax != null
                && yMin > yMax) {

            throw new CommandException(
                    "--y-min must not be greater than --y-max"
            );
        }

        return new ActualBlockYFilter(
                yMin,
                yMax
        );
    }

    private Integer optionalIntegerOption(
            String[] args,
            String optionName
    ) {
        Optional<String> option =
                option(
                        args,
                        optionName
                );

        if (option.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(
                    option.get()
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + option.get()
            );
        }
    }

    private Optional<Integer> splitYOption(String[] args) {
        Optional<String> option =
                option(
                        args,
                        "--split-y"
                );

        if (option.isEmpty()) {
            return Optional.empty();
        }

        try {
            int value =
                    Integer.parseInt(
                            option.get()
                    );

            if (value <= 0) {
                throw new CommandException(
                        "--split-y must be positive"
                );
            }

            return Optional.of(
                    value
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid --split-y: "
                            + option.get()
            );
        }
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

    private boolean includeFoliage(
            String[] args
    ) {
        for (String arg : args) {
            if ("--include-foliage".equals(
                    arg
            )) {
                return true;
            }
        }

        return false;
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

    private record VerticalRange(
            int minY,
            int maxY
    ) {
    }
}
