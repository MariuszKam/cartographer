package cartographer.perf.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf28ColdSnapshotCoverageTest {

    @Test
    void completeRequiresEveryPreparedLayer() {
        assertTrue(new Pf28ColdSnapshotCoverage(
                100,
                true,
                true,
                true,
                true,
                true,
                true
        ).complete());

        assertFalse(new Pf28ColdSnapshotCoverage(
                100,
                true,
                true,
                false,
                true,
                true,
                true
        ).complete());
    }
}
