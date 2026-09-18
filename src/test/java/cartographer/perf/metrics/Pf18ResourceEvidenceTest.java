package cartographer.perf.metrics;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
