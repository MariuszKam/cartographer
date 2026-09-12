package cartographer.cli;

import cartographer.analysis.BlockMatch;
import cartographer.analysis.BlockScanResult;
import cartographer.analysis.BlockScanner;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldPosition;
import cartographer.render.ActualBlockMapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final PrintStream out;
    private final VcdbsReader reader;
    private final SurfaceScanner scanner;
    private final BlockScanner blockScanner;
    private final ActualBlockMapScanner actualBlockMapScanner;
    private final ActualBlockMapRenderer actualBlockMapRenderer;
    private final PngWriter pngWriter;
    private final String subcommand;

    public ScanCommand(
            PrintStream out,
            VcdbsReader reader,
            SurfaceScanner scanner,
            BlockScanner blockScanner,
            String subcommand
    ) {
        this(
                out,
                reader,
                scanner,
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
            SurfaceScanner scanner,
            BlockScanner blockScanner,
            ActualBlockMapScanner actualBlockMapScanner,
            ActualBlockMapRenderer actualBlockMapRenderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.scanner = scanner;
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

        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        progress
                );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        List<ParsedChunk> chunks =
                reader.readChunksAround(
                        savePath,
                        player,
                        radius,
                        diagnostics,
                        progress
                );

        Map<Integer, BlockInfo> registry =
                reader.readBlockRegistry(
                        savePath,
                        progress
                );

        SurfaceScanResult result =
                scanner.scan(
                        chunks,
                        registry,
                        !includeFoliage(
                                args
                        ),
                        progress
                );

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
                        + result.blocks().size()
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

        result.blocks()
                .stream()
                .limit(
                        20
                )
                .forEach(
                        this::printSurfaceBlock
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

    private void runBlocksMap(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: scan blocks-map <save.vcdbs> "
                            + "--match <text> "
                            + "[--radius <blocks>] "
                            + "[--center-x <x> --center-z <z>] "
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
                        1,
                        MAX_BLOCK_MAP_RADIUS
                );

        int scale =
                intOption(
                        args,
                        "--scale",
                        DEFAULT_BLOCK_MAP_SCALE,
                        1,
                        MAX_BLOCK_MAP_SCALE
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
                                DEFAULT_BLOCK_MAP_OUTPUT
                        );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition center =
                center(
                        args
                ).orElseGet(
                        () ->
                                reader.readPlayerPosition(
                                        savePath,
                                        progress
                                )
                );

        int centerX =
                (int) Math.round(
                        center.x()
                );

        int centerZ =
                (int) Math.round(
                        center.z()
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
                        match
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
                output
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
                        + output
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

        WorldPosition center =
                center(
                        args
                ).orElseGet(
                        () ->
                                reader.readPlayerPosition(
                                        savePath,
                                        progress
                                )
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

    private void printSurfaceBlock(
            SurfaceBlock block
    ) {
        out.printf(
                "%d,%d,%d %s %s%n",
                block.worldX(),
                block.y(),
                block.worldZ(),
                block.blockInfo().code(),
                block.blockInfo().materialType()
        );
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
            SurfaceScanResult result
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
                    || value > 8192) {
                throw new CommandException(
                        optionName
                                + " must be between 1 and 8192"
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

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue,
            int minValue,
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

            if (value < minValue
                    || value > maxValue) {
                throw new CommandException(
                        optionName
                                + " must be between "
                                + minValue
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
}
