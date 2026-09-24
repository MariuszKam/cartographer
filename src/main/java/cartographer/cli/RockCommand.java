package cartographer.cli;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMapMode;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.render.PngWriter;
import cartographer.render.RockLegendEntry;
import cartographer.render.RockMapRenderer;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class RockCommand implements Command {
    private static final int DEFAULT_RADIUS = 512;
    private static final int MAX_RADIUS = 8192;
    private static final Path DEFAULT_OUTPUT = Path.of("output", "rock-map.png");

    private final PrintStream out;
    private final SaveSessionFactory sessionFactory;
    private final RenderRockMapUseCase useCase;
    private final PngWriter pngWriter;
    private final String subcommand;

    public RockCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RockMapRenderer renderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this(
                out,
                reader,
                metadataReader,
                renderer,
                pngWriter,
                subcommand,
                new SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader
                )
        );
    }

    public RockCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RockMapRenderer renderer,
            PngWriter pngWriter,
            RenderDataCacheStore renderDataCacheStore,
            SaveSessionFactory sessionFactory,
            String subcommand
    ) {
        this.out = Objects.requireNonNull(out, "out is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        this.useCase = renderer == null
                ? null
                : new RenderRockMapUseCase(
                        reader,
                        renderer,
                        sessionFactory,
                        Objects.requireNonNull(
                                renderDataCacheStore,
                                "render data cache store is required"
                        )
                );
        this.pngWriter = Objects.requireNonNull(
                pngWriter,
                "pngWriter is required"
        );
        this.subcommand = subcommand;
    }

    RockCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RockMapRenderer renderer,
            PngWriter pngWriter,
            String subcommand,
            SaveSessionFactory sessionFactory
    ) {
        this.out = out;
        this.sessionFactory = sessionFactory;
        this.useCase = renderer == null
                ? null
                : new RenderRockMapUseCase(
                        reader,
                        renderer,
                        sessionFactory
                );
        this.pngWriter = pngWriter;
        this.subcommand = subcommand;
    }

    @Override
    public void run(String[] args) {
        switch (subcommand) {
            case "list" -> list(args);
            case "render" -> render(args);
            default -> throw new CommandException("Unknown rock subcommand: " + subcommand);
        }
    }

    private void list(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: rock list <save.vcdbs>");
        }

        RockCatalog catalog = catalog(Path.of(args[0]));
        out.println("ROCK LIST");
        if (catalog.rocks().isEmpty()) {
            out.println("Natural rocks: none");
            return;
        }
        out.println("Natural rocks:");
        catalog.rocks().forEach(rock -> out.println(
                "  " + rock.blockId()
                        + " " + rock.code()
                        + " namespace=" + rock.namespace()
                        + " name=" + rock.rockName()
        ));
    }

    private void render(String[] args) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: rock render <save.vcdbs> [--mode upper-rock] "
                            + "[--radius <blocks>] [--center-x <x> --center-z <z>] "
                            + "[--min-y <y>] [--max-y <exclusive>] [--out <file.png>]"
            );
        }

        RenderRockMapRequest request = request(args);
        if (useCase == null) {
            throw new CommandException("Rock render renderer is unavailable");
        }
        RenderRockMapResult result = useCase.executeRenderOnly(request);
        Path output = Path.of(option(args, "--out").orElse(DEFAULT_OUTPUT.toString()));
        pngWriter.write(result.rendered().image(), output);

        out.println("ROCK MAP");
        out.println("Description: Observed saved geology");
        out.println("Mode: " + request.mode());
        if (request.mode() == RockMapMode.AT_Y) {
            out.println("Y: " + request.y().orElseThrow());
        }
        out.println("Center world: " + result.center().x() + "," + result.center().z());
        out.println("Radius: " + request.radius());
        out.println("Y range: " + result.minY() + ".." + result.maxYExclusive() + " (exclusive)");
        out.println("Output: " + output);
        out.println("Recognized rock block types: " + result.catalog().rocks().size());
        out.println("Observed cells: " + result.rendered().observedCount());
        out.println("No-rock cells: " + result.rendered().noRockCount());
        out.println("Unavailable cells: " + result.rendered().unavailableCount());
        out.println("Read parsed: " + result.diagnostics().parsed());
        out.println("Read skipped: " + result.diagnostics().skipped());
        out.println("Read failed: " + result.diagnostics().failed());
        out.println("Chunk batches: " + result.chunkStats().batchesExecuted());
        out.println("Chunk rows found: " + result.chunkStats().rowsFound());
        out.println("Payload bytes: " + result.chunkStats().payloadBytes());
        out.println("Observed rock distribution:");
        for (RockLegendEntry entry : result.rendered().legend()) {
            out.printf(
                    Locale.ROOT,
                    "  %s: %d (%.2f%%)%n",
                    entry.rock().code(),
                    entry.observedCellCount(),
                    entry.observedPercentage()
            );
        }
    }

    private RenderRockMapRequest request(String[] args) {
        RockMapMode selectedMode = mode(args);
        Optional<String> y = option(args, "--y");
        if (selectedMode == RockMapMode.AT_Y && y.isEmpty()) {
            throw new CommandException("--mode at-y requires --y");
        }
        if (selectedMode == RockMapMode.UPPER_ROCK && y.isPresent()) {
            throw new CommandException("--y is only valid with --mode at-y");
        }
        if (selectedMode == RockMapMode.AT_Y
                && (option(args, "--min-y").isPresent()
                || option(args, "--max-y").isPresent())) {
            throw new CommandException("--min-y and --max-y are only valid for upper-rock");
        }
        return new RenderRockMapRequest(
                Path.of(args[0]),
                selectedMode,
                intOption(args, "--radius", DEFAULT_RADIUS, MAX_RADIUS),
                center(args),
                y.map(value -> integerValue(value, "--y"))
                        .stream()
                        .mapToInt(Integer::intValue)
                        .findFirst(),
                option(args, "--min-y")
                        .map(value -> integerValue(value, "--min-y"))
                        .stream()
                        .mapToInt(Integer::intValue)
                        .findFirst(),
                option(args, "--max-y")
                        .map(value -> integerValue(value, "--max-y"))
                        .stream()
                        .mapToInt(Integer::intValue)
                        .findFirst()
        );
    }

    private RockCatalog catalog(Path savePath) {
        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            return RockCatalog.from(
                    session.snapshot()
                            .blockRegistry()
            );
        }
    }

    private RockMapMode mode(String[] args) {
        return switch (option(args, "--mode").orElse("upper-rock").toLowerCase(Locale.ROOT)) {
            case "upper-rock" -> RockMapMode.UPPER_ROCK;
            case "at-y" -> RockMapMode.AT_Y;
            default -> throw new CommandException("Invalid --mode: " + option(args, "--mode").orElseThrow());
        };
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
        return Optional.of(new WorldPosition(
                doubleOption(x.orElseThrow(), "--center-x"),
                0.0,
                doubleOption(z.orElseThrow(), "--center-z")
        ));
    }

    private int intOption(String[] args, String name, int defaultValue, int maximum) {
        int value = integerOption(args, name, defaultValue);
        if (value <= 0 || value > maximum) {
            throw new CommandException(name + " must be between 1 and " + maximum);
        }
        return value;
    }

    private int integerOption(String[] args, String name, int defaultValue) {
        Optional<String> raw = option(args, name);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.orElseThrow());
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + raw.orElseThrow());
        }
    }

    private int integerValue(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + value);
        }
    }

    private double doubleOption(String value, String name) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + value);
        }
    }

    private int floor(double value) {
        double result = Math.floor(value);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new CommandException("World center is outside the supported block range");
        }
        return (int) result;
    }

    private Optional<String> option(String[] args, String name) {
        for (int index = 0; index < args.length - 1; index++) {
            if (name.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }
}
