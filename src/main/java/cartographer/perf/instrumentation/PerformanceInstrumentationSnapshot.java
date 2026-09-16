package cartographer.perf.instrumentation;

import cartographer.perf.metrics.PerformanceCounters;
import cartographer.perf.metrics.PerformanceStageMetrics;

import java.util.Objects;

/** Immutable stage and counter data captured by one recording session. */
public record PerformanceInstrumentationSnapshot(
        PerformanceStageMetrics stageMetrics,
        PerformanceCounters counters
) {
    public PerformanceInstrumentationSnapshot {
        Objects.requireNonNull(stageMetrics, "stageMetrics is required");
        Objects.requireNonNull(counters, "counters is required");
    }
}
