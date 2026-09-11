package cartographer.cli;

import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MapCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final HomeStore homeStore;
    private final MapRenderer renderer;
    private final PngWriter pngWriter;
    private final String subcommand;

    public MapCommand(PrintStream out, VcdbsReader reader, HomeStore homeStore, MapRenderer renderer, PngWriter pngWriter, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.homeStore = homeStore;
        this.renderer = renderer;
        this.pngWriter = pngWriter;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (!"render".equals(subcommand)) {
            throw new CommandException("Unknown map subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: map render <save.vcdbs> --radius <blocks> --out <map.png>");
        }

        Path savePath = Path.of(args[0]);
        int radius = intOption(args, "--radius", 1024);
        Path output = Path.of(requiredOption(args, "--out"));
        WorldPosition player = reader.readPlayerPosition(savePath, Optional.empty());
        Optional<HomeLocation> home = homeStore.load();
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<MapChunk> chunks = reader.readMapChunksAround(savePath, player, radius, diagnostics);
        BufferedImage image = renderer.render(player, home, chunks, radius);
        pngWriter.write(image, output);

        out.println("MAP");
        out.println("Output: " + output);
        out.println("Parsed mapchunks: " + diagnostics.parsed());
        out.println("Skipped mapchunks: " + diagnostics.skipped());
        out.println("Failed mapchunks: " + diagnostics.failed());
        diagnostics.notes().forEach(note -> out.println("Note: " + note));
        return 0;
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
}
