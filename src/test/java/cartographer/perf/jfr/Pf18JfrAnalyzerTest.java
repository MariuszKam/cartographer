package cartographer.perf.jfr;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18JfrAnalyzerTest {
    @Test
    void lateHeavyKeyCanDisplaceAnEarlyColdKey() {
        Pf18JfrEventAccumulator.SpaceSaving values =
                new Pf18JfrEventAccumulator.SpaceSaving(Pf18JfrEventAccumulator.CAPACITY);
        for (int index = 0; index < 10; index++) {
            values.offer("cold-" + index, 1);
        }

        values.offer("late-hot", 2);

        assertEquals(10, values.values().size());
        assertTrue(values.values().containsKey("late-hot"));
        assertTrue(!values.values().containsKey("cold-0"));
    }

    @Test
    void metadataZeroAndUnavailableAreDistinctAndObservedCountsAreRetained() {
        Pf18JfrEventAccumulator facts = new Pf18JfrEventAccumulator();
        facts.setMetadataTypes(Set.of("jdk.FileRead"));
        facts.observe("jdk.ExecutionSample", Optional.of("frame"), Optional.empty(),
                OptionalLong.empty(), OptionalLong.empty());

        assertTrue(facts.metadataContains("jdk.FileRead"));
        assertTrue(!facts.metadataContains("jdk.FileWrite"));
        assertEquals(1, facts.counts().get("jdk.ExecutionSample"));
    }

    @Test
    void repeatedLateUnitWeightsBecomeHotWithinBoundedStorage() {
        Pf18JfrEventAccumulator.SpaceSaving values =
                new Pf18JfrEventAccumulator.SpaceSaving(Pf18JfrEventAccumulator.CAPACITY);
        for (int index = 0; index < 10; index++) values.offer("cold-" + index, 1);
        for (int index = 0; index < 20; index++) values.offer("late-hot", 1);

        assertEquals(10, values.values().size());
        assertTrue(values.values().containsKey("late-hot"));
        assertTrue(values.values().get("late-hot") > 10);
    }

    @Test
    void allocationAndGcUnavailableFactsRemainPartial() {
        Pf18JfrEventAccumulator facts = new Pf18JfrEventAccumulator();
        facts.observe("jdk.ObjectAllocationSample", Optional.empty(), Optional.of("Class"),
                OptionalLong.empty(), OptionalLong.empty());
        facts.observe("jdk.ObjectAllocationSample", Optional.empty(), Optional.of("Class"),
                OptionalLong.of(4), OptionalLong.empty());
        facts.observe("jdk.GCPhasePause", Optional.empty(), Optional.empty(), OptionalLong.empty(),
                OptionalLong.empty());

        assertEquals("PARTIAL; unavailable for 1 event(s)",
                Pf18JfrAnalyzer.allocationWeightDescription(facts));
        assertEquals("PARTIAL; unavailable for 1 event(s)",
                Pf18JfrAnalyzer.gcDurationDescription(facts));
        assertTrue(!Pf18JfrAnalyzer.allocationWeightDescription(facts).contains("exact"));
    }

    @Test
    void fileIoTextDoesNotClaimObservationWithoutObservedEvents() {
        Pf18JfrEventAccumulator facts = new Pf18JfrEventAccumulator();
        facts.setMetadataTypes(Set.of("jdk.FileRead", "jdk.FileWrite"));

        assertEquals("File I/O event types represented; zero observations",
                Pf18JfrAnalyzer.fileIoDescription(facts));
        assertTrue(!Pf18JfrAnalyzer.fileIoDescription(facts).startsWith("File I/O observed"));
    }

    @Test
    void heavyHitterTieOrderingIsDeterministic() {
        Pf18JfrEventAccumulator.SpaceSaving first =
                new Pf18JfrEventAccumulator.SpaceSaving(2);
        Pf18JfrEventAccumulator.SpaceSaving second =
                new Pf18JfrEventAccumulator.SpaceSaving(2);
        first.offer("b", 1);
        first.offer("a", 1);
        first.offer("c", 1);
        second.offer("b", 1);
        second.offer("a", 1);
        second.offer("c", 1);

        assertEquals(first.values(), second.values());
        assertEquals(2, first.values().size());
    }
}
