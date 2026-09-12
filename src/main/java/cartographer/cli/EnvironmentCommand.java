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
import java.util.Optional;

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
    public int run(
            String[] args
    ) {
        if (!"inspect".equals(subcommand)) {
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

        out.println("ENVIRONMENT");
        out.println("Regions parsed: " + diagnostics.parsed());
        out.println("Regions skipped: " + diagnostics.skipped());
        out.println("Regions failed: " + diagnostics.failed());

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
                                        "Note: " + note
                                )
                );

        return 0;
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

        printClimate(
                profile.climate()
        );

        printForest(
                profile.forest()
        );

        printOcean(
                profile.ocean()
        );

        printIds(
                "Landform",
                profile.landform()
        );

        printIds(
                "GeologicProvince",
                profile.geologicProvince()
        );

        out.println(
                "Derived Cartographer labels: "
                        + profile.labels()
        );
    }

    private void printClimate(
            Optional<ClimateSummary> summary
    ) {
        if (summary.isEmpty()) {
            out.println("Climate: missing");
            return;
        }

        ClimateSummary value =
                summary.get();

        out.printf(
                "Climate: samples=%d avgTemperatureIndex=%.2f avgRainfallIndex=%.2f rawSample=%s%n",
                value.samples(),
                value.averageTemperatureIndex(),
                value.averageRainfallIndex(),
                value.rawSample()
        );
    }

    private void printForest(
            Optional<ForestSummary> summary
    ) {
        if (summary.isEmpty()) {
            out.println("Forest: missing");
            return;
        }

        ForestSummary value =
                summary.get();

        out.printf(
                "Forest: samples=%d rawMin=%d rawMax=%d avgNormalizedDensity=%.3f cartographerClass=%s%n",
                value.samples(),
                value.rawMin(),
                value.rawMax(),
                value.averageNormalizedDensity(),
                value.averageDensityClass()
        );
    }

    private void printOcean(
            Optional<OceanSummary> summary
    ) {
        if (summary.isEmpty()) {
            out.println("Ocean: missing");
            return;
        }

        OceanSummary value =
                summary.get();

        out.printf(
                "Ocean: samples=%d rawMin=%d rawMax=%d avgRaw=%.2f%n",
                value.samples(),
                value.rawMin(),
                value.rawMax(),
                value.averageRawValue()
        );
    }

    private void printIds(
            String name,
            Optional<IdMapSummary> summary
    ) {
        if (summary.isEmpty()) {
            out.println(
                    name
                            + ": missing"
            );
            return;
        }

        IdMapSummary value =
                summary.get();

        out.println(
                name
                        + ": samples="
                        + value.samples()
                        + " distinct="
                        + value.distinctCount()
                        + " dominantIds="
                        + value.dominantIds()
                        + " names=unavailable"
        );
    }

    private void printFailureReasons(
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {
            return;
        }

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
