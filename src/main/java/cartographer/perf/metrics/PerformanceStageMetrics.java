package cartographer.perf.metrics;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Immutable nanosecond timings keyed only by the known execution stages. */
public record PerformanceStageMetrics(Map<PerformanceStage, Long> durationsNanoseconds) {
    public PerformanceStageMetrics {
        Objects.requireNonNull(durationsNanoseconds, "durationsNanoseconds is required");

        EnumMap<PerformanceStage, Long> copy = new EnumMap<>(PerformanceStage.class);
        for (Map.Entry<PerformanceStage, Long> entry : durationsNanoseconds.entrySet()) {
            PerformanceStage stage = Objects.requireNonNull(entry.getKey(), "stage is required");
            Long duration = Objects.requireNonNull(
                    entry.getValue(),
                    "duration for " + stage + " is required"
            );
            if (duration < 0) {
                throw new IllegalArgumentException(
                        "duration for " + stage + " must not be negative"
                );
            }
            if (copy.put(stage, duration) != null) {
                throw new IllegalArgumentException("duplicate duration for " + stage);
            }
        }
        durationsNanoseconds = Collections.unmodifiableMap(copy);
    }

    public long durationNanoseconds(PerformanceStage stage) {
        Objects.requireNonNull(stage, "stage is required");
        return durationsNanoseconds.getOrDefault(stage, 0L);
    }
}
