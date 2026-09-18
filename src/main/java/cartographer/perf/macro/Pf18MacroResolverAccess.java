package cartographer.perf.macro;

import cartographer.perf.workload.WorkloadSpec;

/** Small package boundary for the JFR package to use the shared resolver. */
public final class Pf18MacroResolverAccess {
    private Pf18MacroResolverAccess() {
    }

    public static WorkloadSpec resolve(String workloadId) {
        return MacroWorkloadResolver.resolve(workloadId);
    }
}
