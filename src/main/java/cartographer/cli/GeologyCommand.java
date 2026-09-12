package cartographer.cli;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.GeologyReport;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.geology.RockStrataAnalyzer;
import cartographer.geology.RockStrataSummary;
import cartographer.geology.RockStratumSummary;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GeologyCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final SurfaceScanner surfaceScanner;
    private final GeologyAnalyzer geologyAnalyzer;
    private final String subcommand;

    public GeologyCommand(PrintStream out, VcdbsReader reader, SurfaceScanner surfaceScanner, GeologyAnalyzer geologyAnalyzer, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.surfaceScanner = surfaceScanner;
        this.geologyAnalyzer = geologyAnalyzer;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        switch (subcommand) {
            case "surface" ->
                    runSurface(
                            args
                    );

            case "strata" ->
                    runStrata(
                            args
                    );

            default ->
                    throw new CommandException("Unknown geology subcommand: " + subcommand);
        }

        return 0;
    }

    private void runSurface(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: geology surface <save.vcdbs> --radius <blocks> [--center-x <x> --center-z <z>]");
        }

        Path savePath = Path.of(args[0]);
        int radius = intOption(args, "--radius", 512);
        ProgressReporter progress = new ProgressReporter(out);
        WorldPosition center = center(args).orElseGet(() -> reader.readPlayerPosition(savePath, Optional.empty(), progress));
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ParsedChunk> chunks = reader.readChunksAround(savePath, center, radius, diagnostics, progress);
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath, progress);
        SurfaceScanResult surface = surfaceScanner.scan(chunks, registry, true, progress);
        GeologyReport report = geologyAnalyzer.analyze(surface.blocks());

        out.println("GEOLOGY SURFACE");
        out.println("Chunks parsed: " + diagnostics.parsed());
        out.println("Chunks skipped: " + diagnostics.skipped());
        out.println("Chunks failed: " + diagnostics.failed());
        out.println("Surface samples: " + report.samples());
        out.println("Geological samples: " + report.geologicalSamples());
        out.println("Unknown samples: " + report.unknownSamples());
        printMap("Rock families", report.rockFamilies());
        printMap("Material types", report.materialTypes());
        diagnostics.notes().forEach(note -> out.println("Note: " + note));
    }

    private void runStrata(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: geology strata <save.vcdbs>");
        }

        Path savePath = Path.of(args[0]);
        ProgressReporter progress = new ProgressReporter(out);
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ServerMapRegion> regions = reader.readMapRegions(savePath, diagnostics, progress);
        RockStrataAnalyzer strataAnalyzer = new RockStrataAnalyzer();
        GeologicProvinceInterpreter provinceInterpreter = new GeologicProvinceInterpreter();

        out.println("GEOLOGY STRATA");
        out.println("Regions parsed: " + diagnostics.parsed());
        out.println("Regions skipped: " + diagnostics.skipped());
        out.println("Regions failed: " + diagnostics.failed());
        printFailureReasons(diagnostics);

        for (ServerMapRegion region : regions) {
            RockStrataSummary strataSummary = strataAnalyzer.summarize(region);
            Optional<GeologicProvinceSummary> province = provinceInterpreter.summarize(region);

            if (strataSummary.strata().isEmpty() && province.isEmpty()) {
                continue;
            }

            out.println();
            out.println("Region " + region.coordinate().x() + "," + region.coordinate().z());
            out.println("  RockStrata maps: " + strataSummary.strata().size());

            for (RockStratumSummary stratum : strataSummary.strata()) {
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
                                + " minRawId="
                                + stratum.minRawId()
                                + " maxRawId="
                                + stratum.maxRawId()
                                + " distinct="
                                + stratum.distinctCount()
                                + " dominantRawIds="
                                + stratum.dominantRawIds()
                );
            }

            if (province.isPresent()) {
                GeologicProvinceSummary summary = province.get();
                out.println(
                        "  GeologicProvinceMap: samples="
                                + summary.samples()
                                + " distinct="
                                + summary.distinctCount()
                                + " dominantRawIds="
                                + summary.dominantIds()
                );
            } else {
                out.println("  GeologicProvinceMap: missing");
            }
        }

        diagnostics.notes().forEach(note -> out.println("Note: " + note));
    }

    private void printMap(String title, Map<String, Integer> values) {
        out.println(title + ":");
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> out.printf("  %s: %d%n", entry.getKey(), entry.getValue()));
    }

    private void printFailureReasons(ReadDiagnostics diagnostics) {
        if (diagnostics.failureReasons().isEmpty()) {
            return;
        }

        out.println("Failure reasons:");
        diagnostics.failureReasonLines()
                .forEach(line -> out.println("  " + line));
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

    private double parseDouble(String value, String optionName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + optionName + ": " + value);
        }
    }
}
