package cartographer.perf.macro;

import cartographer.perf.metrics.Pf18ResourceEvidence;
import java.nio.file.Path;

/** External process boundary used by PROCESS_COLD; never falls back in-process. */
@FunctionalInterface
public interface Pf18ProcessLauncher {
    Pf18ProcessResult launch(Path save, Path cacheRoot, String workloadId, Path evidence)
            throws Exception;

    record Pf18ProcessResult(Pf18IterationEvidence evidence, long parentElapsedNanoseconds,
                             Pf18ResourceEvidence resourceEvidence) {
        public Pf18ProcessResult {
            if (parentElapsedNanoseconds < 0) {
                throw new IllegalArgumentException("parent elapsed time must not be negative");
            }
            java.util.Objects.requireNonNull(evidence);
            java.util.Objects.requireNonNull(resourceEvidence);
        }
    }
}
