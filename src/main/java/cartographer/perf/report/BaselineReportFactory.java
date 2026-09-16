package cartographer.perf.report;

import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.safety.SaveSafetyResult;

import java.util.Objects;

/** Adapts validated baseline and save-safety evidence without recomputation. */
public final class BaselineReportFactory {
    private BaselineReportFactory() {
    }

    public static BaselineReport from(
            ReferenceBaseline baseline,
            SaveSafetyResult safetyResult
    ) {
        Objects.requireNonNull(baseline, "baseline is required");
        Objects.requireNonNull(safetyResult, "safetyResult is required");
        return new BaselineReport(
                baseline.gitCommitSha(),
                baseline.workloadId(),
                baseline.saveFingerprint(),
                baseline.executionMode(),
                baseline.environment(),
                baseline.warmupCount(),
                baseline.measuredIterationCount(),
                baseline.correctnessFingerprint(),
                baseline.summary().minWallClockNanoseconds(),
                baseline.summary().p50WallClockNanoseconds(),
                baseline.summary().p95WallClockNanoseconds(),
                baseline.summary().maxWallClockNanoseconds(),
                baseline.samples().stream()
                        .map(sample -> new BaselineReportSample(
                                sample.iterationIndex(), sample.wallClockNanoseconds()
                        ))
                        .toList(),
                safetyResult.status(),
                safetyResult.violations()
        );
    }
}
