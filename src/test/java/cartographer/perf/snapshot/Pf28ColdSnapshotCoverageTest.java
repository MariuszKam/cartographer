package cartographer.perf.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf28ColdSnapshotCoverageTest {

    @Test
    void completeRequiresEveryPreparedLayer() {
        assertTrue(completeFresh().complete());

        assertFalse(new Pf28ColdSnapshotCoverage(
                100,
                0, 100,
                0, 100,
                0, 10,
                0, 100,
                0, 800,
                true,
                true,
                false,
                true,
                true,
                true
        ).complete());
    }

    @Test
    void coldBuildProofRejectsAnyDerivedHit() {
        assertTrue(completeFresh().coldBuildProven());

        assertFalse(new Pf28ColdSnapshotCoverage(
                100,
                1, 99,
                0, 100,
                0, 10,
                0, 100,
                0, 800,
                true,
                true,
                true,
                true,
                true,
                true
        ).coldBuildProven());
    }

    private Pf28ColdSnapshotCoverage completeFresh() {
        return new Pf28ColdSnapshotCoverage(
                100,
                0, 100,
                0, 100,
                0, 10,
                0, 100,
                0, 800,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }
}
