package cartographer.cli;

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
    private final String subcommand;

    public ScanCommand(PrintStream out, VcdbsReader reader, SurfaceScanner scanner, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.scanner = scanner;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (!"surface".equals(subcommand)) {
            throw new CommandException("Unknown scan subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: scan surface <save.vcdbs> --radius <blocks> [--include-foliage]");
        }

        Path savePath = Path.of(args[0]);
        int radius = intOption(args, "--radius", 256);
        WorldPosition player = reader.readPlayerPosition(savePath, Optional.empty());
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ParsedChunk> chunks = reader.readChunksAround(savePath, player, radius, diagnostics);
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath);
        SurfaceScanResult result = scanner.scan(chunks, registry, !hasFlag(args, "--include-foliage"));

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

    private void printSurfaceBlock(SurfaceBlock block) {
        out.printf("%d,%d,%d %s %s%n",
                block.worldX(),
                block.y(),
                block.worldZ(),
                block.blockInfo().code(),
                block.blockInfo().materialType());
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
}
