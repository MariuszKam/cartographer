package cartographer.perf.jfr;

import cartographer.perf.macro.Pf18ProductionOperationFactory;

import java.nio.file.Path;

/** Opt-in reviewer entry point for separate PF-1.8 JFR evidence. */
public final class Pf18JfrMain {
    private Pf18JfrMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: pf18Jfr <save> <cacheRoot> <gitSha> <workload> <output>");
        }
        Pf18JfrSummary summary = new Pf18JfrRunner(
                new Pf18ProductionOperationFactory(Path.of(args[4]).resolve("state")))
                .profile(Path.of(args[0]), Path.of(args[1]), args[2], args[3], Path.of(args[4]));
        System.out.println("PF-1.8 JFR recording: " + summary.recording());
        System.out.println("PF-1.8 JFR summary: " + summary.summary());
        System.out.println("Diagnostic/profiling run — not timing baseline");
    }
}
