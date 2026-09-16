package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkOperation;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.benchmark.BenchmarkRunner;
import cartographer.perf.metrics.ExecutionMode;

import java.nio.file.Files;
import java.util.Objects;

/**
 * Composition layer for reviewer-controlled JFR recordings. One invocation
 * owns one recording and the recording covers warmups and measured iterations.
 */
public final class JfrBenchmarkProfiler {
    private final BenchmarkRunner benchmarkRunner;
    private final JfrRecordingFactory recordingFactory;

    public JfrBenchmarkProfiler() {
        this(new BenchmarkRunner(), new JdkJfrRecordingFactory());
    }

    public JfrBenchmarkProfiler(
            BenchmarkRunner benchmarkRunner,
            JfrRecordingFactory recordingFactory
    ) {
        this.benchmarkRunner = Objects.requireNonNull(
                benchmarkRunner,
                "benchmarkRunner is required"
        );
        this.recordingFactory = Objects.requireNonNull(
                recordingFactory,
                "recordingFactory is required"
        );
    }

    public JfrRecordingResult profile(
            JfrRecordingPlan recordingPlan,
            BenchmarkPlan benchmarkPlan,
            BenchmarkOperation operation
    ) {
        Objects.requireNonNull(recordingPlan, "recordingPlan is required");
        Objects.requireNonNull(benchmarkPlan, "benchmarkPlan is required");
        Objects.requireNonNull(operation, "operation is required");
        rejectUnsupportedExecutionMode(benchmarkPlan);
        rejectExistingDestination(recordingPlan);

        try (JfrRecordingController recording = recordingFactory.open(recordingPlan)) {
            Objects.requireNonNull(recording, "recording factory returned null");
            recording.configure();
            recording.start();
            BenchmarkRunResult benchmarkResult = benchmarkRunner.run(
                    benchmarkPlan,
                    operation
            );
            rejectExistingDestination(recordingPlan);
            recording.stop();
            recording.dump(recordingPlan.destination());
            return new JfrRecordingResult(
                    benchmarkResult,
                    recordingPlan.destination(),
                    recordingPlan.maxSizeBytes(),
                    recordingPlan.configuration()
            );
        }
    }

    private static void rejectExistingDestination(JfrRecordingPlan plan) {
        if (Files.exists(plan.destination())) {
            throw new JfrProfilingException(
                    "JFR destination already exists: " + plan.destination()
            );
        }
    }

    private static void rejectUnsupportedExecutionMode(BenchmarkPlan plan) {
        if (plan.executionMode() != ExecutionMode.JVM_WARM) {
            throw new UnsupportedOperationException(
                    "JFR in-process profiling supports only JVM_WARM"
            );
        }
    }
}
