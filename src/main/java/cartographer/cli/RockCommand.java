package cartographer.cli;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockChunkCoverage;
import cartographer.geology.rock.RockColumnScanner;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.geology.rock.RockAtYScanner;
import cartographer.geology.rock.RockLegendEntry;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.application.OreChunkPositionPlanner;
import cartographer.render.PngWriter;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RockCommand implements Command {
    private static final int DEFAULT_RADIUS = 512;
    private static final int MAX_RADIUS = 8192;
    private static final Path DEFAULT_OUTPUT = Path.of("output", "rock-map.png");

    private final PrintStream out;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final RockMapRenderer renderer;
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
        this.out = out;
        this.reader = reader;
        this.metadataReader = metadataReader;
        this.renderer = renderer;
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

        Path savePath = Path.of(args[0]);
        RockMapMode mode = mode(args);

        ProgressReporter progress = new ProgressReporter(out);
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        WorldMetadata metadata = metadataReader.read(savePath, progress);
        WorldPosition center = center(args).orElseGet(
                () -> reader.readPlayerPosition(savePath, progress)
        );
        int radius = intOption(args, "--radius", DEFAULT_RADIUS, MAX_RADIUS);
        Optional<String> requestedY = option(args, "--y");
        int minY = mode == RockMapMode.UPPER_ROCK
                ? integerOption(args, "--min-y", 0)
                : requestedY.map(value -> integerValue(value, "--y")).orElse(0);
        int maxY = mode == RockMapMode.UPPER_ROCK
                ? integerOption(args, "--max-y", metadata.mapSizeY())
                : minY + 1;
        if (mode == RockMapMode.AT_Y) {
            if (requestedY.isEmpty()) {
                throw new CommandException("--mode at-y requires --y");
            }
            if (option(args, "--min-y").isPresent()
                    || option(args, "--max-y").isPresent()) {
                throw new CommandException("--min-y and --max-y are only valid for upper-rock");
            }
            if (minY < 0 || minY >= metadata.mapSizeY()) {
                throw new CommandException("--y must be within the world vertical range");
            }
        } else if (requestedY.isPresent()) {
            throw new CommandException("--y is only valid with --mode at-y");
        }
        if (minY >= maxY) {
            throw new CommandException("--min-y must be less than --max-y");
        }

        RockCatalog catalog = catalog(savePath);
        if (catalog.rocks().isEmpty()) {
            throw new CommandException(
                    "No natural rock blocks (rock-*) were discovered in the save registry"
            );
        }

        List<ChunkPosition> positions = new OreChunkPositionPlanner().plan(
                metadata,
                floor(center.x()),
                floor(center.z()),
                radius,
                new ActualBlockYFilter(minY, maxY - 1)
        );
        List<ParsedChunk> chunks = new ArrayList<>();
        List<ChunkPosition> available = new ArrayList<>();
        SelectiveChunkStreamStats readStats =
                reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                savePath,
                positions,
                catalog.rockBlockIds().stream().mapToInt(Integer::intValue).toArray(),
                diagnostics,
                visit -> {
                    if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                        available.add(visit.position());
                        chunks.add(visit.chunk());
                    } else if (visit.status() == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
                        available.add(visit.position());
                    }
                },
                        progress
                );

        RockChunkCoverage coverage = RockChunkCoverage.fromChunkPositions(available);
        RockMap rockMap = mode == RockMapMode.UPPER_ROCK
                ? new RockColumnScanner().scan(
                        chunks,
                        catalog,
                        center,
                        radius,
                        minY,
                        maxY,
                        coverage
                )
                : new RockAtYScanner().scan(
                        chunks,
                        catalog,
                        coverage,
                        center,
                        radius,
                        minY
                );
        RockMapRenderResult rendered = renderer.render(rockMap);
        Path output = Path.of(option(args, "--out").orElse(DEFAULT_OUTPUT.toString()));
        progress.start("Writing PNG");
        pngWriter.write(rendered.image(), output);
        progress.done("PNG written");

        out.println("ROCK MAP");
        out.println("Description: Observed saved geology");
        out.println("Mode: " + mode);
        if (mode == RockMapMode.AT_Y) {
            out.println("Y: " + minY);
        }
        out.println("Center world: " + center.x() + "," + center.z());
        out.println("Radius: " + radius);
        out.println("Y range: " + minY + ".." + maxY + " (exclusive)");
        out.println("Output: " + output);
        out.println("Recognized rock block types: " + catalog.rocks().size());
        out.println("Observed cells: " + rendered.observedCount());
        out.println("No-rock cells: " + rendered.noRockCount());
        out.println("Unavailable cells: " + rendered.unavailableCount());
        out.println("Read parsed: " + diagnostics.parsed());
        out.println("Read skipped: " + diagnostics.skipped());
        out.println("Read failed: " + diagnostics.failed());
        out.println("Chunk batches: " + readStats.batchesExecuted());
        out.println("Chunk rows found: " + readStats.rowsFound());
        out.println("Payload bytes: " + readStats.payloadBytes());
        out.println("Observed rock distribution:");
        for (RockLegendEntry entry : rendered.legend()) {
            out.printf(
                    Locale.ROOT,
                    "  %s: %d (%.2f%%)%n",
                    entry.rock().code(),
                    entry.observedCellCount(),
                    entry.observedPercentage()
            );
        }
    }

    private RockCatalog catalog(Path savePath) {
        return RockCatalog.from(reader.readBlockRegistry(savePath));
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
