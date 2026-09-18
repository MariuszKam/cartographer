package cartographer.perf.macro;

import cartographer.perf.workload.WorkloadSpec;

import java.nio.file.Path;
import java.util.List;

/** Creates one declared production operation for a campaign phase. */
@FunctionalInterface
public interface Pf18MacroOperationFactory {
    Pf18MacroOperation create(Path save, Path cacheRoot, WorkloadSpec workload);

    default Pf18MacroOperation createAuthoritative(Path save, WorkloadSpec workload) {
        return create(save, null, workload);
    }

    default void prepareCache(Path save, Path cacheRoot, WorkloadSpec workload) {
        create(save, cacheRoot, workload).execute();
    }

    default List<String> cacheEvidence(Path save, Path cacheRoot, WorkloadSpec workload) {
        return List.of();
    }

    @FunctionalInterface
    interface Pf18MacroOperation {
        Pf18IterationEvidence execute();
    }
}
