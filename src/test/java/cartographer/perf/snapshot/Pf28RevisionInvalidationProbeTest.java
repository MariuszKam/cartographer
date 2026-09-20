package cartographer.perf.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf28RevisionInvalidationProbeTest {

    @TempDir
    Path root;

    @Test
    void changedSourceRevisionCannotSeePreviousSnapshotHeader() {
        assertTrue(
                new Pf28RevisionInvalidationProbe().verify(root)
        );
    }
}
