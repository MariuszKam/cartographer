package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MapCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final MapRenderer renderer;
    private final PngWriter pngWriter;
    private final String subcommand;

    public MapCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            MapRenderer renderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.metadataReader = metadataReader;
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
        int scale = intOption(args, "--scale", 1);
        RenderStyle style = RenderStyle.parse(option(args, "--style").orElse("simple"));
        RenderOptions options = new RenderOptions(radius, scale, style, RenderLayer.parse(option(args, "--layers").orElse("")));
        Path output = Path.of(requiredOption(args, "--out"));
        ProgressReporter progress = new ProgressReporter(out);
        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        Optional.empty(),
                        progress
                );
        WorldPosition center = center(args).orElse(player);
        Optional<HomeLocation> home = absoluteHome(
                savePath,
                progress
        );
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<MapChunk> chunks = reader.readMapChunksAround(savePath, center, radius, diagnostics, progress);
        RenderedMap rendered = renderer.render(center, player, home, chunks, options, progress);
        progress.start("Writing PNG");
        pngWriter.write(rendered.image(), output);
        progress.done("PNG written");

        out.println("MAP");
        out.println("Output: " + output);
        out.println("Image: " + rendered.report().width() + "x" + rendered.report().height());
        out.println("Style: " + rendered.report().style());
        out.println("Layers: " + rendered.report().layers());
        out.println("Tiles drawn: " + rendered.report().tilesDrawn());
        out.println("Markers: " + rendered.report().markerCount());
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
            int max = "--scale".equals(optionName) ? 16 : 8192;
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
}
