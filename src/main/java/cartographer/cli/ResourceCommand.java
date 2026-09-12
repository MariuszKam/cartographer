package cartographer.cli;

import cartographer.model.ServerMapRegion;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceCandidate;
import cartographer.resource.ResourceSummary;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ResourceCommand implements Command {
    private static final int DEFAULT_TOP =
            20;

    private static final int MAX_TOP =
            100;

    private final PrintStream out;
    private final VcdbsReader reader;
    private final ResourceAnalyzer analyzer;
    private final String subcommand;

    public ResourceCommand(
            PrintStream out,
            VcdbsReader reader,
            ResourceAnalyzer analyzer,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.analyzer = analyzer;
        this.subcommand = subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        return switch (subcommand) {
            case "list" ->
                    list(
                            args
                    );

            case "inspect" ->
                    inspect(
                            args
                    );

            case "search" ->
                    search(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown resource subcommand: "
                                    + subcommand
                    );
        };
    }

    private int list(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: resource list <save.vcdbs>"
            );
        }

        LoadedResources loaded =
                load(
                        Path.of(
                                args[0]
                        )
                );

        out.println("RESOURCE LIST");
        printDiagnostics(
                loaded.diagnostics()
        );

        List<String> keys =
                analyzer.resourceKeys(
                        loaded.regions()
                );

        if (keys.isEmpty()) {
            out.println("Resource maps: none");
        } else {
            out.println("Resource maps:");
            keys.forEach(
                    key ->
                            out.println(
                                    "  " + key
                            )
            );
        }

        return 0;
    }

    private int inspect(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource inspect <save.vcdbs> <resource>"
            );
        }

        LoadedResources loaded =
                load(
                        Path.of(
                                args[0]
                        )
                );

        out.println("RESOURCE INSPECT");
        printDiagnostics(
                loaded.diagnostics()
        );

        Optional<String> selected =
                selectResource(
                        loaded.regions(),
                        args[1]
                );

        if (selected.isEmpty()) {
            return 0;
        }

        ResourceSummary summary =
                analyzer.summarize(
                                loaded.regions(),
                                selected.get(),
                                10
                        )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Resource map disappeared during inspection: "
                                                        + selected.get()
                                        )
                        );

        printSummary(
                summary
        );

        return 0;
    }

    private int search(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: resource search <save.vcdbs> <resource> [--top <n>]"
            );
        }

        int top =
                topOption(
                        args
                );

        LoadedResources loaded =
                load(
                        Path.of(
                                args[0]
                        )
                );

        out.println("RESOURCE SEARCH");
        printDiagnostics(
                loaded.diagnostics()
        );

        Optional<String> selected =
                selectResource(
                        loaded.regions(),
                        args[1]
                );

        if (selected.isEmpty()) {
            return 0;
        }

        ResourceSummary summary =
                analyzer.summarize(
                                loaded.regions(),
                                selected.get(),
                                top
                        )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "Resource map disappeared during search: "
                                                        + selected.get()
                                        )
                        );

        out.println("Resource: " + summary.resourceKey());
        out.println("Top candidates:");

        List<ResourceCandidate> candidates =
                summary.strongestCandidates();

        if (candidates.isEmpty()) {
            out.println("  none");
        }

        for (int index = 0; index < candidates.size(); index++) {
            ResourceCandidate candidate =
                    candidates.get(
                            index
                    );

            out.printf(
                    Locale.ROOT,
                    "%d. region %d,%d cell %d,%d raw=%d relativeSignal=%.3f%n",
                    index + 1,
                    candidate.regionCoordinate().x(),
                    candidate.regionCoordinate().z(),
                    candidate.localX(),
                    candidate.localZ(),
                    candidate.rawValue(),
                    candidate.relativeIntensity()
            );

            out.printf(
                    "   approximate world center: %d,%d%n",
                    candidate.approximateWorldX(),
                    candidate.approximateWorldZ()
            );
        }

        return 0;
    }

    private LoadedResources load(
            Path savePath
    ) {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        List<ServerMapRegion> regions =
                reader.readMapRegions(
                        savePath,
                        diagnostics,
                        progress
                );

        return new LoadedResources(
                regions,
                diagnostics
        );
    }

    private Optional<String> selectResource(
            List<ServerMapRegion> regions,
            String query
    ) {
        List<String> matches =
                analyzer.matchingKeys(
                        regions,
                        query
                );

        if (matches.isEmpty()) {
            out.println(
                    "Resource: "
                            + query
            );
            out.println("Matching resource maps: none");
            printAvailableResources(
                    regions
            );

            return Optional.empty();
        }

        if (matches.size() > 1) {
            out.println(
                    "Resource: "
                            + query
            );
            out.println("Matching resource maps are ambiguous:");
            matches.forEach(
                    match ->
                            out.println(
                                    "  " + match
                            )
            );

            return Optional.empty();
        }

        return Optional.of(
                matches.get(0)
        );
    }

    private void printSummary(
            ResourceSummary summary
    ) {
        out.println("Resource: " + summary.resourceKey());
        out.println("Regions with map: " + summary.regions());
        out.println("Inner cells: " + summary.cells());
        out.println("Raw min: " + summary.rawMin());
        out.println("Raw max: " + summary.rawMax());
        out.printf(
                Locale.ROOT,
                "Raw average: %.3f%n",
                summary.averageRawValue()
        );
        out.println("Distinct raw values: " + summary.distinctValues());
        out.println("Strongest candidate sample:");

        if (summary.strongestCandidates()
                .isEmpty()) {
            out.println("  none");
        }

        for (ResourceCandidate candidate : summary.strongestCandidates()) {
            out.printf(
                    Locale.ROOT,
                    "  region %d,%d cell %d,%d raw=%d relativeSignal=%.3f approximateWorldCenter=%d,%d%n",
                    candidate.regionCoordinate().x(),
                    candidate.regionCoordinate().z(),
                    candidate.localX(),
                    candidate.localZ(),
                    candidate.rawValue(),
                    candidate.relativeIntensity(),
                    candidate.approximateWorldX(),
                    candidate.approximateWorldZ()
            );
        }
    }

    private void printAvailableResources(
            List<ServerMapRegion> regions
    ) {
        List<String> keys =
                analyzer.resourceKeys(
                        regions
                );

        if (keys.isEmpty()) {
            return;
        }

        out.println("Available resource maps:");
        keys.forEach(
                key ->
                        out.println(
                                "  " + key
                        )
        );
    }

    private void printDiagnostics(
            ReadDiagnostics diagnostics
    ) {
        out.println("Regions parsed: " + diagnostics.parsed());
        out.println("Regions skipped: " + diagnostics.skipped());
        out.println("Regions failed: " + diagnostics.failed());

        if (!diagnostics.failureReasons()
                .isEmpty()) {
            out.println("Failure reasons:");
            diagnostics.failureReasonLines()
                    .forEach(
                            line ->
                                    out.println(
                                            "  " + line
                                    )
                    );
        }
    }

    private int topOption(
            String[] args
    ) {
        Optional<String> option =
                option(
                        args,
                        "--top"
                );

        if (option.isEmpty()) {
            return DEFAULT_TOP;
        }

        try {
            int value =
                    Integer.parseInt(
                            option.get()
                    );

            if (value < 1
                    || value > MAX_TOP) {
                throw new CommandException(
                        "--top must be between 1 and "
                                + MAX_TOP
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid --top: "
                            + option.get()
            );
        }
    }

    private Optional<String> option(
            String[] args,
            String optionName
    ) {
        for (int index = 2; index < args.length - 1; index++) {
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

    private record LoadedResources(
            List<ServerMapRegion> regions,
            ReadDiagnostics diagnostics
    ) {
    }
}
