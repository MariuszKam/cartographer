package cartographer.perf.report;

import java.util.Objects;

/** Renders baseline evidence as deterministic plain text; it performs no I/O. */
public final class BaselineReportRenderer {
    public String render(BaselineReport report) {
        Objects.requireNonNull(report, "report is required");
        StringBuilder output = new StringBuilder();
        line(output, "VS Cartographer Performance Baseline");
        line(output, "====================================");
        output.append('\n');
        section(output, "Identity", "--------");
        line(output, "Git commit: " + report.gitCommitSha());
        line(output, "Workload: " + report.workloadId());
        line(output, "Save fingerprint: " + report.saveFingerprint());
        line(output, "Execution mode: " + report.executionMode().name());
        output.append('\n');
        section(output, "Environment", "-----------");
        line(output, "Java: " + report.environment().javaVersion());
        line(output, "JVM vendor: " + report.environment().jvmVendor());
        line(output, "OS: " + report.environment().osName()
                + " " + report.environment().osVersion());
        line(output, "Architecture: " + report.environment().osArchitecture());
        line(output, "Available processors: " + report.environment().availableProcessors());
        line(output, "Max heap bytes: " + report.environment().configuredMaxHeapBytes());
        output.append('\n');
        section(output, "Methodology", "-----------");
        line(output, "Warmups: " + report.warmupCount());
        line(output, "Measured iterations: " + report.measuredIterationCount());
        output.append('\n');
        section(output, "Correctness", "-----------");
        line(output, "Result fingerprint: " + report.correctnessFingerprint().sha256Hex());
        output.append('\n');
        section(output, "Wall-clock", "----------");
        line(output, "Min: " + report.minWallClockNanoseconds() + " ns");
        line(output, "P50: " + report.p50WallClockNanoseconds() + " ns");
        line(output, "P95: " + report.p95WallClockNanoseconds() + " ns");
        line(output, "Max: " + report.maxWallClockNanoseconds() + " ns");
        output.append('\n');
        section(output, "Measured samples", "----------------");
        for (BaselineReportSample sample : report.samples()) {
            line(output, sample.iterationIndex() + ": "
                    + sample.wallClockNanoseconds() + " ns");
        }
        output.append('\n');
        section(output, "Save safety", "-----------");
        line(output, "Status: " + report.saveSafetyStatus().name());
        if (report.saveSafetyViolations().isEmpty()) {
            line(output, "Violations: none");
        } else {
            line(output, "Violations:");
            report.saveSafetyViolations().forEach(violation -> line(
                    output,
                    "- " + violation.type().name() + ": " + violation.path()
            ));
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
