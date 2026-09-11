package cartographer.cli;

import cartographer.atlas.AtlasRenderer;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AtlasCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final HomeStore homeStore;
    private final AtlasRenderer atlasRenderer;
    private final String subcommand;

    public AtlasCommand(PrintStream out, VcdbsReader reader, HomeStore homeStore, AtlasRenderer atlasRenderer, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.homeStore = homeStore;
        this.atlasRenderer = atlasRenderer;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (!"render".equals(subcommand)) {
            throw new CommandException("Unknown atlas subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: atlas render <save.vcdbs> --center-x <x> --center-z <z> --radius <blocks> --levels <n> --out <directory>");
        }

        Path savePath = Path.of(args[0]);
        WorldPosition center = center(args).orElseGet(() -> reader.readPlayerPosition(savePath, Optional.empty(), new ProgressReporter(out)));
        int radius = intOption(args, "--radius", 1024);
        int levels = intOption(args, "--levels", 3);
        Path output = Path.of(requiredOption(args, "--out"));
        Optional<HomeLocation> home = homeStore.load();
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        ProgressReporter progress = new ProgressReporter(out);
        List<MapChunk> chunks = reader.readMapChunksAround(savePath, center, radius, diagnostics, progress);
        atlasRenderer.render(output, center, home, chunks, radius, levels, progress);

        out.println("ATLAS");
        out.println("Output: " + output);
        out.println("Levels: " + levels);
        out.println("Parsed mapchunks: " + diagnostics.parsed());
        out.println("Skipped mapchunks: " + diagnostics.skipped());
        out.println("Failed mapchunks: " + diagnostics.failed());
        diagnostics.notes().forEach(note -> out.println("Note: " + note));
        return 0;
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

    private int intOption(String[] args, String optionName, int defaultValue) {
        Optional<String> option = option(args, optionName);
        if (option.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(option.get());
            int max = "--levels".equals(optionName) ? 8 : 8192;
            if (value <= 0 || value > max) {
                throw new CommandException(optionName + " must be between 1 and " + max);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + optionName + ": " + option.get());
        }
    }

    private String requiredOption(String[] args, String optionName) {
        return option(args, optionName).orElseThrow(() -> new CommandException("Missing option: " + optionName));
    }

    private Optional<String> option(String[] args, String optionName) {
        for (int index = 1; index < args.length - 1; index++) {
            if (optionName.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }

    private double parseDouble(String value, String optionName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + optionName + ": " + value);
        }
    }
}
