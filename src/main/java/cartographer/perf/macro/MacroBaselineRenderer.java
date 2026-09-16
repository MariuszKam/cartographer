package cartographer.perf.macro;

import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.baseline.ReferenceBaselineSample;

import java.util.Objects;

/** Renders only evidence collected by the macro baseline run. */
public final class MacroBaselineRenderer {
    public String render(ReferenceBaseline baseline) {
        Objects.requireNonNull(baseline, "baseline is required");
        StringBuilder output = new StringBuilder();
        line(output, "VS Cartographer Macro Performance Baseline");
        line(output, "===========================================");
        output.append('\n');

        section(output, "Identity", "--------");
        line(output, "Git commit: " + baseline.gitCommitSha());
        line(output, "Workload: " + baseline.workloadId());
        line(output, "Save fingerprint: " + baseline.saveFingerprint());
        line(output, "Execution mode: " + baseline.executionMode().name());
        output.append('\n');

        section(output, "Environment", "-----------");
        line(output, "Java: " + baseline.environment().javaVersion());
        line(output, "JVM vendor: " + baseline.environment().jvmVendor());
        line(output, "OS: " + baseline.environment().osName()
                + " " + baseline.environment().osVersion());
        line(output, "Architecture: " + baseline.environment().osArchitecture());
        line(output, "Available processors: "
                + baseline.environment().availableProcessors());
        line(output, "Max heap bytes: "
                + baseline.environment().configuredMaxHeapBytes());
        output.append('\n');

        section(output, "Methodology", "-----------");
        line(output, "Warmups: " + baseline.warmupCount());
        line(output, "Measured iterations: " + baseline.measuredIterationCount());
        line(output, "Execution mode semantics: JVM_WARM");
        line(output, "OS filesystem cache state: uncontrolled");
        line(output, "Save safety: separate gate; not evaluated by this benchmark command");
        output.append('\n');

        section(output, "Correctness", "-----------");
        line(output, "Result fingerprint: "
                + baseline.correctnessFingerprint().sha256Hex());
        output.append('\n');

        section(output, "Wall-clock", "----------");
        line(output, "Min: " + baseline.summary().minWallClockNanoseconds() + " ns");
        line(output, "P50: " + baseline.summary().p50WallClockNanoseconds() + " ns");
        line(output, "P95: " + baseline.summary().p95WallClockNanoseconds() + " ns");
        line(output, "Max: " + baseline.summary().maxWallClockNanoseconds() + " ns");
        output.append('\n');

        section(output, "Measured samples", "----------------");
        for (ReferenceBaselineSample sample : baseline.samples()) {
            line(output, sample.iterationIndex() + ": "
                    + sample.wallClockNanoseconds() + " ns");
        }
        return output.toString();
    }

    private static void section(StringBuilder output, String title, String underline) {
        line(output, title);
        line(output, underline);
    }

    private static void line(StringBuilder output, String value) {
        output.append(value).append('\n');
    }
}
