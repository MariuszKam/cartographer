package cartographer.perf.macro;

import java.nio.file.Path;
import java.util.Objects;

/** Opt-in command entry point for real-save ROCK macro baseline evidence. */
public final class MacroBaselineMain {
    private MacroBaselineMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: perfBaseline <save> <workload> <gitSha> <outputDirectory>");
        }
        String workload = required(args[1], "workload");
        String gitSha = required(args[2], "gitSha");
        MacroBaselineResult result = new MacroBaselineRunner().run(
                Path.of(required(args[0], "save")),
                workload,
                gitSha,
                MacroBaselineEnvironment.capture(),
                Path.of(required(args[3], "outputDirectory"))
        );
        System.out.println("Macro baseline report generated:");
        System.out.println(result.reportPath().toAbsolutePath().normalize());
        System.out.println("Workload: " + result.baseline().workloadId());
        System.out.println("Execution mode: " + result.baseline().executionMode().name());
        System.out.println("Measured iterations: "
                + result.baseline().measuredIterationCount());
        System.out.println("Save safety: separate gate; not evaluated by this command");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
