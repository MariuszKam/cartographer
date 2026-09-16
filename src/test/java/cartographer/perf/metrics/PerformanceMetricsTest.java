package cartographer.perf.metrics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceMetricsTest {
    private static final PerformanceEnvironment ENVIRONMENT = new PerformanceEnvironment(
            "25.0.1", "Example JVM", "Windows", "11", "amd64", 8, 512L * 1024 * 1024
    );
    private static final PerformanceCounters COUNTERS = new PerformanceCounters(
            1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    );
    private static final PerformanceStageMetrics STAGES = new PerformanceStageMetrics(
            Map.of(PerformanceStage.SQLITE_READ, 10L, PerformanceStage.DECODE, 20L)
    );
    private static final PerformanceMetrics METRICS = new PerformanceMetrics(
            100L, 90L, 200L, 300L, 2L, 4L, STAGES, COUNTERS
    );

    @Test
    void validConstructionAndDeterministicEquality() {
        PerformanceRun first = run();
        PerformanceRun second = run();

        assertEquals(first, second);
        assertEquals(10L, first.metrics().stageMetrics().durationNanoseconds(
                PerformanceStage.SQLITE_READ
        ));
        assertEquals(ExecutionMode.JVM_WARM, first.executionMode());
    }

    @Test
    void rejectsNegativeDurationsBytesAndCounters() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceStageMetrics(
                Map.of(PerformanceStage.DECODE, -1L)
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceMetrics(
                -1L, 0L, 0L, 0L, 0L, 0L, STAGES, COUNTERS
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceCounters(
                0L, 0L, -1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
        ));
    }

    @Test
    void rejectsInvalidRunIdentifiersAndIterationCounts() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRun(
                " ", "workload", "save", ExecutionMode.PROCESS_COLD, 0, 1,
                ENVIRONMENT, METRICS
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRun(
                "abc", " ", "save", ExecutionMode.PROCESS_COLD, 0, 1,
                ENVIRONMENT, METRICS
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRun(
                "abc", "workload", "save", ExecutionMode.PROCESS_COLD, -1, 1,
                ENVIRONMENT, METRICS
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceRun(
                "abc", "workload", "save", ExecutionMode.PROCESS_COLD, 0, 0,
                ENVIRONMENT, METRICS
        ));
    }

    @Test
    void stageMetricsAreImmutableToCallers() {
        PerformanceStageMetrics stages = new PerformanceStageMetrics(
                Map.of(PerformanceStage.RENDER, 40L)
        );

        assertThrows(UnsupportedOperationException.class, () ->
                stages.durationsNanoseconds().put(PerformanceStage.DECODE, 50L)
        );
        assertEquals(40L, stages.durationNanoseconds(PerformanceStage.RENDER));
    }

    private static PerformanceRun run() {
        return new PerformanceRun(
                "abc123", "small-save-surface", "save-sha256", ExecutionMode.JVM_WARM,
                3, 5, ENVIRONMENT, METRICS
        );
    }
}
