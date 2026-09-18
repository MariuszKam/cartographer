package cartographer.perf.macro;

import cartographer.perf.safety.SaveSafetySnapshot;

import java.nio.file.Path;

@FunctionalInterface
interface Pf18SafetySnapshotProvider {
    SaveSafetySnapshot capture(Path save);
}
