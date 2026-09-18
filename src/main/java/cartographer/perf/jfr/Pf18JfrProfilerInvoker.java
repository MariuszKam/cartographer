package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkOperation;
import cartographer.perf.benchmark.BenchmarkPlan;

@FunctionalInterface
interface Pf18JfrProfilerInvoker {
    JfrRecordingResult profile(JfrRecordingPlan recordingPlan, BenchmarkPlan benchmarkPlan,
                               BenchmarkOperation operation);
}
