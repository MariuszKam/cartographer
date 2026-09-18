package cartographer.perf.metrics;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18ResourceEvidenceTest {
    @Test
    void unsupportedMeasurementsAreExplicitlyUnavailable() {
        Pf18ResourceEvidence evidence = Pf18ResourceEvidence.unavailable();

        assertTrue(evidence.processCpuNanoseconds().isEmpty());
        assertTrue(evidence.allocatedBytes().isEmpty());
        assertTrue(evidence.rssBytes().isEmpty());
        assertEquals("CPU: UNAVAILABLE (method: UNAVAILABLE)",
                evidence.render("CPU", evidence.processCpuNanoseconds(), evidence.cpuMethod()));
    }

    @Test
    void resourceSnapshotIsImmutableAndRetainsMeasuredValues() {
        Pf18ResourceEvidence evidence = new Pf18ResourceEvidence(
                OptionalLong.of(12), OptionalLong.of(34), OptionalLong.of(1),
                OptionalLong.of(2), OptionalLong.empty(), OptionalLong.empty(),
                "test CPU", "test heap", "test GC", "UNAVAILABLE", "UNAVAILABLE");

        assertEquals(12, evidence.processCpuNanoseconds().getAsLong());
        assertEquals(34, evidence.peakHeapBytes().getAsLong());
        assertEquals("test GC", evidence.gcMethod());
        assertEquals("test heap", evidence.heapMethod());
        assertTrue(evidence.render("RSS", evidence.rssBytes(), evidence.rssMethod())
                .contains("UNAVAILABLE"));
    }

    @Test
    void samplerReportsItsProductionHeapMethodology() {
        Pf18ResourceSampler.Measured<String> measured = new Pf18ResourceSampler()
                .measure(() -> "sample");

        if (measured.evidence().peakHeapBytes().isPresent()) {
            assertTrue(measured.evidence().heapMethod()
                    .contains("aggregate per-heap-pool peak-used sum"));
            assertTrue(measured.evidence().heapMethod()
                    .contains("not a simultaneous process high-water mark"));
        } else {
            assertEquals("UNAVAILABLE", measured.evidence().heapMethod());
        }
    }

    @Test
    void negativePresentMeasurementsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.of(-1),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.empty(),
                OptionalLong.of(-1), OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.of(-1), OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.of(-1),
                OptionalLong.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.of(-1), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> evidence(OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.of(-1)));
    }

    private static Pf18ResourceEvidence evidence(OptionalLong cpu, OptionalLong heap,
                                                 OptionalLong gcCount, OptionalLong gcTime,
                                                 OptionalLong allocation, OptionalLong rss) {
        return new Pf18ResourceEvidence(cpu, heap, gcCount, gcTime, allocation, rss,
                "CPU", "heap", "GC", "allocation", "RSS");
    }
}
