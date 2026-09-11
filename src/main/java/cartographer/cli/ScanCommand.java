package cartographer.cli;

import cartographer.analysis.BlockMatch;
import cartographer.analysis.BlockScanResult;
import cartographer.analysis.BlockScanner;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScanCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final SurfaceScanner scanner;
    private final BlockScanner blockScanner;
    private final String subcommand;

    public ScanCommand(PrintStream out, VcdbsReader reader, SurfaceScanner scanner, BlockScanner blockScanner, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.scanner = scanner;
        this.blockScanner = blockScanner;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if ("blocks".equals(subcommand)) {
            return scanBlocks(args);
        }
        if (!"surface".equals(subcommand)) {
            throw new CommandException("Unknown scan subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: scan surface <save.vcdbs> --radius <blocks> [--include-foliage]");
        }

        Path savePath = Path.of(args[0]);
        int radius = intOption(args, "--radius", 256);
        ProgressReporter progress = new ProgressReporter(out);
        WorldPosition player = reader.readPlayerPosition(savePath, Optional.empty(), progress);
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ParsedChunk> chunks = reader.readChunksAround(savePath, player, radius, diagnostics, progress);
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath, progress);
        SurfaceScanResult result = scanner.scan(chunks, registry, !hasFlag(args, "--include-foliage"), progress);

        out.println("SURFACE");
        out.println("Chunks parsed: " + diagnostics.parsed());
        out.println("Chunks skipped: " + diagnostics.skipped());
        out.println("Chunks failed: " + diagnostics.failed());
        out.println("Registry blocks: " + registry.size());
        out.println("Columns scanned: " + result.columnsScanned());
        out.println("Empty columns: " + result.emptyColumns());
        out.println("Surface blocks: " + result.blocks().size());

        result.blocks().stream().limit(20).forEach(this::printSurfaceBlock);
        diagnostics.notes().forEach(note -> out.println("Note: " + note));
        return 0;
    }

    private int scanBlocks(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: scan blocks <save.vcdbs> --match <text> [--radius <blocks>] [--limit <n>]");
        }
        Path savePath = Path.of(args[0]);
        String match = option(args, "--match").orElseThrow(() -> new CommandException("Missing option: --match"));
        int radius = intOption(args, "--radius", 256);
        int limit = intOption(args, "--limit", 100);
        ProgressReporter progress = new ProgressReporter(out);
        WorldPosition center = center(args).orElseGet(() -> reader.readPlayerPosition(savePath, Optional.empty(), progress));
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ParsedChunk> chunks = reader.readChunksAround(savePath, center, radius, diagnostics, progress);
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath, progress);
        BlockScanResult result = blockScanner.scan(chunks, registry, match, limit, progress);

        out.println("BLOCK SCAN");
        out.println("Match: " + match);
        out.println("Chunks parsed: " + diagnostics.parsed());
        out.println("Chunks skipped: " + diagnostics.skipped());
        out.println("Chunks failed: " + diagnostics.failed());
        out.println("Blocks scanned: " + result.blocksScanned());
        out.println("Matches: " + result.matches().size());
        out.println("Truncated: " + result.truncated());
        result.matches().forEach(this::printBlockMatch);
        diagnostics.notes().forEach(note -> out.println("Note: " + note));
        return 0;
    }

    private void printSurfaceBlock(SurfaceBlock block) {
        out.printf("%d,%d,%d %s %s%n",
                block.worldX(),
                block.y(),
                block.worldZ(),
                block.blockInfo().code(),
                block.blockInfo().materialType());
    }

    private void printBlockMatch(BlockMatch match) {
        out.printf("%d,%d,%d %s %s%n",
                match.worldX(),
                match.y(),
                match.worldZ(),
                match.blockInfo().code(),
                match.blockInfo().materialType());
    }

    private int intOption(String[] args, String optionName, int defaultValue) {
        Optional<String> option = option(args, optionName);
        if (option.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(option.get());
            if (value <= 0 || value > 8192) {
                throw new CommandException(optionName + " must be between 1 and 8192");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + optionName + ": " + option.get());
        }
    }

    private Optional<String> option(String[] args, String optionName) {
        for (int index = 1; index < args.length - 1; index++) {
            if (optionName.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private Optional<WorldPosition> center(String[] args) {
        Optional<String> x = option(args, "--center-x");
        Optional<String> z = option(args, "--center-z");
        if (x.isEmpty() && z.isEmpty()) {
            return Optional.empty();
        }
        if (x.isEmpty() || z.isEmpty()) {
            throw new CommandException("--center-x and --center-z must be used together");
        }
        return Optional.of(new WorldPosition(parseDouble(x.get(), "--center-x"), 0.0, parseDouble(z.get(), "--center-z")));
    }

    private double parseDouble(String value, String optionName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + optionName + ": " + value);
        }
    }
}
