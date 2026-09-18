package cartographer.perf.macro;

import cartographer.perf.metrics.ExecutionMode;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Opt-in reviewer entry point for PF-1.8 macro campaigns. */
public final class Pf18MacroMain {
    private Pf18MacroMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: pf18Macro <save> <cacheRoot> <gitSha> <workload> <mode> <output>");
        }
        Path save = Path.of(required(args[0], "save"));
        Path cache = Path.of(required(args[1], "cacheRoot"));
        String gitSha = required(args[2], "gitSha");
        String workload = required(args[3], "workload");
        ExecutionMode mode = ExecutionMode.valueOf(required(args[4], "mode")
                .toUpperCase(Locale.ROOT));
        Path output = Path.of(required(args[5], "output"));
        Pf18MacroReport report = new Pf18MacroRunner(
                new Pf18ProductionOperationFactory(output.resolve("state")),
                new Pf18JavaProcessLauncher(),
                MacroBaselineEnvironment.capture()).run(
                        save, cache, workload, gitSha, mode, output);
        System.out.println("PF-1.8 macro report: " + report.outputPath());
        System.out.println("Evidence verdict: "
                + (report.evidenceIsValid() ? "FACTUAL" : "INVALID"));
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
