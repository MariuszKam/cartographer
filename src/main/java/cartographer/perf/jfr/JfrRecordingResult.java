package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkRunResult;

import java.nio.file.Path;
import java.util.Objects;

/** Benchmark semantics plus metadata for the successfully written recording. */
public record JfrRecordingResult(
        BenchmarkRunResult benchmarkResult,
        Path destination,
        long maxSizeBytes,
        JfrConfiguration configuration
) {
    public JfrRecordingResult {
        Objects.requireNonNull(benchmarkResult, "benchmarkResult is required");
        Objects.requireNonNull(destination, "destination is required");
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive");
        }
        Objects.requireNonNull(configuration, "configuration is required");
    }
}
