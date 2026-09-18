package cartographer.perf.jfr;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pf18JfrSummaryTest {
    @Test
    void summaryIsDiagnosticOnlyAndRetainsDeterministicOrdering() {
        Pf18JfrSummary summary = new Pf18JfrSummary(
                Path.of("recording.jfr"), Path.of("summary.txt"), true,
                List.of("jdk.ExecutionSample: 2 observed", "jdk.FileRead: UNAVAILABLE"));

        assertEquals(List.of("jdk.ExecutionSample: 2 observed", "jdk.FileRead: UNAVAILABLE"),
                summary.lines());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.lines().add("changed"));
    }

    @Test
    void nonDiagnosticSummaryCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new Pf18JfrSummary(
                Path.of("recording.jfr"), Path.of("summary.txt"), false, List.of()));
    }
}
