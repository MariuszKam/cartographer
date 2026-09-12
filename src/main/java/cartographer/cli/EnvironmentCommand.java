package cartographer.cli;

import cartographer.environment.ClimateSummary;
import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.environment.ForestSummary;
import cartographer.environment.IdMapSummary;
import cartographer.environment.OceanSummary;
import cartographer.model.ServerMapRegion;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public class EnvironmentCommand implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final EnvironmentInterpreter interpreter;
    private final String subcommand;

    public EnvironmentCommand(
            PrintStream out,
            VcdbsReader reader,
            EnvironmentInterpreter interpreter,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.interpreter = interpreter;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (!"inspect".equals(
                subcommand
        )) {
            throw new CommandException(
                    "Unknown environment subcommand: "
                            + subcommand
            );
        }

        if (args.length < 1) {
            throw new CommandException(
                    "Usage: environment inspect <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

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

        out.println(
                "ENVIRONMENT"
        );

        out.println(
                "Regions parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Regions skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Regions failed: "
                        + diagnostics.failed()
        );

        printFailureReasons(
                diagnostics
        );

        for (ServerMapRegion region : regions) {
            printProfile(
                    interpreter.interpret(
                            region
                    )
            );
        }

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private void printProfile(
            EnvironmentProfile profile
    ) {
        out.println();

        out.println(
                "Region "
                        + profile.coordinate().x()
                        + ","
                        + profile.coordinate().z()
        );

        profile.climate()
                .ifPresentOrElse(
                        this::printClimate,
                        () ->
                                printMissing(
                                        "Climate"
                                )
                );

        profile.forest()
                .ifPresentOrElse(
                        this::printForest,
                        () ->
                                printMissing(
                                        "Forest"
                                )
                );

        profile.ocean()
                .ifPresentOrElse(
                        this::printOcean,
                        () ->
                                printMissing(
                                        "Ocean"
                                )
                );

        profile.landform()
                .ifPresentOrElse(
                        summary ->
                                printIds(
                                        "Landform",
                                        summary
                                ),
                        () ->
                                printMissing(
                                        "Landform"
                                )
                );

        profile.geologicProvince()
                .ifPresentOrElse(
                        summary ->
                                printIds(
                                        "GeologicProvince",
                                        summary
                                ),
                        () ->
                                printMissing(
                                        "GeologicProvince"
                                )
                );

        out.println(
                "Derived Cartographer labels: "
                        + profile.labels()
        );
    }

    private void printClimate(
            ClimateSummary summary
    ) {
        out.printf(
                "Climate: samples=%d avgTemperatureIndex=%.2f avgRainfallIndex=%.2f rawSample=%s%n",
                summary.samples(),
                summary.averageTemperatureIndex(),
                summary.averageRainfallIndex(),
                summary.rawSample()
        );
    }

    private void printForest(
            ForestSummary summary
    ) {
        out.printf(
                "Forest: samples=%d rawMin=%d rawMax=%d avgNormalizedDensity=%.3f cartographerClass=%s%n",
                summary.samples(),
                summary.rawMin(),
                summary.rawMax(),
                summary.averageNormalizedDensity(),
                summary.averageDensityClass()
        );
    }

    private void printOcean(
            OceanSummary summary
    ) {
        out.printf(
                "Ocean: samples=%d rawMin=%d rawMax=%d avgRaw=%.2f%n",
                summary.samples(),
                summary.rawMin(),
                summary.rawMax(),
                summary.averageRawValue()
        );
    }

    private void printIds(
            String name,
            IdMapSummary summary
    ) {
        out.println(
                name
                        + ": samples="
                        + summary.samples()
                        + " distinct="
                        + summary.distinctCount()
                        + " dominantIds="
                        + summary.dominantIds()
                        + " names=unavailable"
        );
    }

    private void printMissing(
            String name
    ) {
        out.println(
                name
                        + ": missing"
        );
    }

    private void printFailureReasons(
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                "Failure reasons:"
        );

        diagnostics.failureReasonLines()
                .forEach(
                        line ->
                                out.println(
                                        "  " + line
                                )
                );
    }
}