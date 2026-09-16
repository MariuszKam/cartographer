package cartographer.perf.metrics;

import java.util.Objects;

/** Required measured quantities use nanoseconds, bytes, and long counts. */
public record PerformanceMetrics(
        long wallClockNanoseconds,
        long cpuNanoseconds,
        long peakHeapBytes,
        long allocatedBytes,
        long gcCount,
        long gcPauseNanoseconds,
        PerformanceStageMetrics stageMetrics,
        PerformanceCounters counters
) {
    public PerformanceMetrics {
        requireNonNegative(wallClockNanoseconds, "wallClockNanoseconds");
        requireNonNegative(cpuNanoseconds, "cpuNanoseconds");
        requireNonNegative(peakHeapBytes, "peakHeapBytes");
        requireNonNegative(allocatedBytes, "allocatedBytes");
        requireNonNegative(gcCount, "gcCount");
        requireNonNegative(gcPauseNanoseconds, "gcPauseNanoseconds");
        Objects.requireNonNull(stageMetrics, "stageMetrics is required");
        Objects.requireNonNull(counters, "counters is required");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
