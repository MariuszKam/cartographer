package cartographer.perf.comparison;

import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.PerformanceEnvironment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Validates raw benchmark output before it can enter comparison logic. */
public final class CandidateMeasurementFactory {
    private CandidateMeasurementFactory() {
    }

    public static CandidateMeasurement from(
            String gitCommitSha,
            String saveFingerprint,
            PerformanceEnvironment environment,
            BenchmarkRunResult runResult
    ) {
        String normalizedSha = exactGitSha(gitCommitSha);
        String normalizedSaveFingerprint = required(saveFingerprint, "saveFingerprint");
        Objects.requireNonNull(environment, "environment is required");
        Objects.requireNonNull(runResult, "runResult is required");
        if (runResult.status() != BenchmarkExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("only SUCCESS runs can become candidates");
        }

        var plan = runResult.plan();
        if (runResult.warmups().size() != plan.warmupCount()
                || runResult.warmups().stream().anyMatch(iteration -> !iteration.successful())) {
            throw new IllegalArgumentException("warmup results do not match the successful plan");
        }

        List<BenchmarkIterationResult> measured = runResult.measuredIterations();
        if (measured.isEmpty() || measured.size() != plan.measuredIterationCount()) {
            throw new IllegalArgumentException("measured results do not match the plan");
        }

        ResultFingerprint expectedFingerprint = null;
        List<CandidateMeasurementSample> samples = new ArrayList<>(measured.size());
        for (int position = 0; position < measured.size(); position++) {
            BenchmarkIterationResult iteration = measured.get(position);
            if (!iteration.successful() || iteration.iterationIndex() != position) {
                throw new IllegalArgumentException(
                        "measured iterations must be successful and indexed 0..N-1"
                );
            }
            ResultFingerprint fingerprint = iteration.fingerprint().orElseThrow(
                    () -> new IllegalArgumentException(
                            "successful measured iteration requires a fingerprint"
                    )
            );
            if (expectedFingerprint == null) {
                expectedFingerprint = fingerprint;
            } else if (!expectedFingerprint.equals(fingerprint)) {
                throw new IllegalArgumentException("measured fingerprints must be identical");
            }
            samples.add(new CandidateMeasurementSample(
                    position,
                    iteration.wallClockNanoseconds(),
                    fingerprint,
                    iteration.instrumentation()
            ));
        }

        List<Long> sortedDurations = samples.stream()
                .map(CandidateMeasurementSample::wallClockNanoseconds)
                .sorted(Comparator.naturalOrder())
                .toList();
        CandidateMeasurementSummary summary = new CandidateMeasurementSummary(
                sortedDurations.get(0),
                nearestRank(sortedDurations, 50),
                nearestRank(sortedDurations, 95),
                sortedDurations.get(sortedDurations.size() - 1)
        );
        return new CandidateMeasurement(
                normalizedSha,
                plan.workload().id(),
                normalizedSaveFingerprint,
                plan.executionMode(),
                plan.warmupCount(),
                plan.measuredIterationCount(),
                environment,
                expectedFingerprint,
                samples,
                summary
        );
    }

    private static long nearestRank(List<Long> sortedValues, int percentile) {
        long rank = (percentile * (long) sortedValues.size() + 99L) / 100L;
        return sortedValues.get((int) rank - 1);
    }

    private static String exactGitSha(String value) {
        String normalized = required(value, "gitCommitSha").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitCommitSha must be a full 40-character SHA");
        }
        return normalized;
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
