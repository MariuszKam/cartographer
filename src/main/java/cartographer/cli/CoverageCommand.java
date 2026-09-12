package cartographer.cli;

import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CoverageCommand implements Command {
    private final PrintStream out;

    private final VcdbsReader reader;

    private final WorldMetadataReader metadataReader;

    private final HomeStore homeStore;

    private final RegionCoverageAnalyzer analyzer;

    private final RegionCoverageRenderer renderer;

    private final PngWriter pngWriter;

    private final String subcommand;

    public CoverageCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            RegionCoverageAnalyzer analyzer,
            RegionCoverageRenderer renderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        this.out =
                out;

        this.reader =
                reader;

        this.metadataReader =
                metadataReader;

        this.homeStore =
                homeStore;

        this.analyzer =
                analyzer;

        this.renderer =
                renderer;

        this.pngWriter =
                pngWriter;

        this.subcommand =
                subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        switch (subcommand) {
            case "inspect" ->
                    inspect(
                            args
                    );

            case "render" ->
                    render(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown coverage subcommand: "
                                    + subcommand
                    );
        }

        return 0;
    }

    private void inspect(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: coverage inspect <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        LoadedCoverage loaded =
                load(
                        savePath
                );

        out.println(
                "COVERAGE"
        );

        printReadDiagnostics(
                loaded.diagnostics()
        );

        printSummary(
                loaded.summary()
        );
    }

    private void render(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: coverage render <save.vcdbs> --out <coverage.png>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        Path output =
                Path.of(
                        requiredOutput(
                                args
                        )
                );

        LoadedCoverage loaded =
                load(
                        savePath
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player =
                reader.readPlayerPosition(
                        savePath,
                        Optional.empty(),
                        progress
                );

        Optional<HomeLocation> home =
                absoluteHome(
                        savePath,
                        loaded.metadata()
                );

        progress.start(
                "Rendering coverage"
        );

        BufferedImage image =
                renderer.render(
                        loaded.summary(),
                        player,
                        home
                );

        progress.done(
                "Coverage rendered"
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
                "COVERAGE MAP"
        );

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "Image: "
                        + image.getWidth()
                        + "x"
                        + image.getHeight()
        );

        printReadDiagnostics(
                loaded.diagnostics()
        );

        printSummary(
                loaded.summary()
        );
    }

    private LoadedCoverage load(
            Path savePath
    ) {
        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        List<ServerMapRegion> regions =
                reader.readMapRegions(
                        savePath,
                        diagnostics,
                        progress
                );

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        RegionCoverageSummary summary =
                analyzer.analyze(
                        regions,
                        metadata
                );

        return new LoadedCoverage(
                metadata,
                diagnostics,
                summary
        );
    }

    private void printReadDiagnostics(
            ReadDiagnostics diagnostics
    ) {
        out.println(
                "Mapregions parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Mapregions skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Mapregions failed: "
                        + diagnostics.failed()
        );

        if (diagnostics.failureReasons()
                .isEmpty()) {
            printNotes(
                    diagnostics
            );

            return;
        }

        out.println(
                "Failure reasons:"
        );

        diagnostics.failureReasonLines()
                .forEach(
                        line ->
                                out.println(
                                        "  "
                                                + line
                                )
                );

        printNotes(
                diagnostics
        );
    }

    private void printNotes(
            ReadDiagnostics diagnostics
    ) {
        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private void printSummary(
            RegionCoverageSummary summary
    ) {
        if (summary.empty()) {
            out.println(
                    "Available mapregions: 0"
            );

            return;
        }

        out.println(
                "Available mapregions: "
                        + summary.presentCells()
        );

        out.println(
                "Region bounds: X "
                        + summary.minRegionX()
                        + ".."
                        + summary.maxRegionX()
                        + ", Z "
                        + summary.minRegionZ()
                        + ".."
                        + summary.maxRegionZ()
        );

        out.println(
                "Region grid: "
                        + summary.gridWidth()
                        + "x"
                        + summary.gridHeight()
        );

        out.println(
                "Bounding cells: "
                        + summary.possibleCells()
        );

        out.println(
                "Missing cells inside bounds: "
                        + summary.missingCells()
        );

        out.printf(
                Locale.ROOT,
                "Coverage inside bounds: %.2f%%%n",
                summary.coveragePercentage()
        );

        out.println(
                "World bounds: X "
                        + summary.worldMinX()
                        + ".."
                        + (summary.worldMaxXExclusive() - 1)
                        + ", Z "
                        + summary.worldMinZ()
                        + ".."
                        + (summary.worldMaxZExclusive() - 1)
        );

        out.printf(
                Locale.ROOT,
                "Display bounds: X %.0f..%.0f, Z %.0f..%.0f%n",
                summary.displayMinX(),
                summary.displayMaxXExclusive() - 1.0,
                summary.displayMinZ(),
                summary.displayMaxZExclusive() - 1.0
        );
    }

    private Optional<HomeLocation> absoluteHome(
            Path savePath,
            WorldMetadata metadata
    ) {
        Optional<HomeLocation> displayHome =
                homeStore.load(
                        savePath
                );

        if (displayHome.isEmpty()) {
            return Optional.empty();
        }

        WorldPosition absolute =
                metadata.toAbsolute(
                        new DisplayPosition(
                                displayHome.get()
                                        .x(),
                                0.0,
                                displayHome.get()
                                        .z()
                        )
                );

        return Optional.of(
                new HomeLocation(
                        absolute.x(),
                        absolute.z()
                )
        );
    }

    private String requiredOutput(
            String[] args
    ) {
        return option(
                args,
                "--out"
        ).orElseThrow(
                () ->
                        new CommandException(
                                "Missing option: --out"
                        )
        );
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

    private record LoadedCoverage(
            WorldMetadata metadata,
            ReadDiagnostics diagnostics,
            RegionCoverageSummary summary
    ) {
    }
}