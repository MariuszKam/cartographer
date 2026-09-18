package cartographer.perf.jfr;

import cartographer.perf.safety.SaveSafetySnapshot;

import java.nio.file.Path;

@FunctionalInterface
interface Pf18JfrSafetySnapshotProvider {
    SaveSafetySnapshot capture(Path save);
}
