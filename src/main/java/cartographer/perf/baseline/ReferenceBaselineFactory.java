package cartographer.perf.baseline;

import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.PerformanceEnvironment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Creates baselines only from structurally valid successful benchmark output. */
public final class ReferenceBaselineFactory {
    private ReferenceBaselineFactory() {
    }

    public static ReferenceBaseline from(
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
            throw new IllegalArgumentException(
                    "only SUCCESS benchmark runs can become reference baselines"
            );
        }

        var plan = runResult.plan();
        if (runResult.warmups().size() != plan.warmupCount()) {
            throw new IllegalArgumentException("warmup result count does not match the plan");
        }
        if (runResult.warmups().stream().anyMatch(iteration -> !iteration.successful())) {
            throw new IllegalArgumentException("successful runs cannot contain failed warmups");
        }

        List<BenchmarkIterationResult> measured = runResult.measuredIterations();
        if (measured.isEmpty() || measured.size() != plan.measuredIterationCount()) {
            throw new IllegalArgumentException(
                    "measured result count must be positive and match the plan"
            );
        }

        ResultFingerprint expectedFingerprint = null;
        List<ReferenceBaselineSample> samples = new ArrayList<>(measured.size());
        for (int position = 0; position < measured.size(); position++) {
            BenchmarkIterationResult iteration = measured.get(position);
            if (!iteration.successful()) {
                throw new IllegalArgumentException("failed measured iteration cannot be a baseline");
            }
            if (iteration.iterationIndex() != position) {
                throw new IllegalArgumentException(
                        "measured iteration indexes must be 0..N-1 in stored order"
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
                throw new IllegalArgumentException(
                        "measured fingerprints must be identical"
                );
            }
            samples.add(new ReferenceBaselineSample(
                    iteration.iterationIndex(),
                    iteration.wallClockNanoseconds(),
                    fingerprint,
                    iteration.instrumentation()
            ));
        }

        List<Long> sortedDurations = samples.stream()
                .map(ReferenceBaselineSample::wallClockNanoseconds)
                .sorted(Comparator.naturalOrder())
                .toList();
        ReferenceBaselineSummary summary = new ReferenceBaselineSummary(
                sortedDurations.get(0),
                nearestRank(sortedDurations, 50),
                nearestRank(sortedDurations, 95),
                sortedDurations.get(sortedDurations.size() - 1)
        );
        return new ReferenceBaseline(
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
        String normalized = required(value, "gitCommitSha").toLowerCase(java.util.Locale.ROOT);
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
