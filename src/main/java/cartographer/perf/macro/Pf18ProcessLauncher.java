package cartographer.perf.macro;

import java.nio.file.Path;

/** External process boundary used by PROCESS_COLD; never falls back in-process. */
@FunctionalInterface
public interface Pf18ProcessLauncher {
    Pf18ProcessResult launch(Path save, Path cacheRoot, String workloadId, Path evidence)
            throws Exception;

    record Pf18ProcessResult(Pf18IterationEvidence evidence, long parentElapsedNanoseconds) {
        public Pf18ProcessResult {
            if (parentElapsedNanoseconds < 0) {
                throw new IllegalArgumentException("parent elapsed time must not be negative");
            }
        }
    }
}
