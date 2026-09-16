package cartographer.perf.benchmark;

import cartographer.perf.workload.WorkloadSpec;

@FunctionalInterface
public interface BenchmarkOperation {
    BenchmarkOperationResult execute(WorkloadSpec workload);
}
