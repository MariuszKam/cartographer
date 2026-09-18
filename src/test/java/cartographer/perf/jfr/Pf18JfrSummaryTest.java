package cartographer.perf.jfr;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pf18JfrSummaryTest {
    @Test
    void summaryIsDiagnosticOnlyAndRetainsDeterministicOrdering() {
        Pf18JfrSummary summary = new Pf18JfrSummary(
                Path.of("recording.jfr"), Path.of("summary.txt"), true,
                identity(),
                List.of("jdk.ExecutionSample: 2 observed", "jdk.FileRead: UNAVAILABLE"));

        assertEquals(List.of("jdk.ExecutionSample: 2 observed", "jdk.FileRead: UNAVAILABLE"),
                summary.lines());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.lines().add("changed"));
    }

    @Test
    void nonDiagnosticSummaryCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new Pf18JfrSummary(
                Path.of("recording.jfr"), Path.of("summary.txt"), false, identity(), List.of()));
    }

    @Test
    void identityRetainsReviewConfiguration() {
        Pf18JfrCampaignIdentity identity = identity();

        assertEquals("a".repeat(40), identity.gitSha());
        assertEquals("CACHE_WARM", identity.declaredProfileState());
        assertEquals("profile", identity.jfrConfiguration());
        assertEquals(Optional.of("b".repeat(64)), identity.semanticFingerprint());
    }

    private static Pf18JfrCampaignIdentity identity() {
        return new Pf18JfrCampaignIdentity(
                "a".repeat(40), "MAP_R1024", "MAP", 1024, "CACHE_WARM", "profile",
                256L * 1024 * 1024, Path.of("world.vcdbs"), "PASS",
                Optional.of("b".repeat(64)), Optional.of("c".repeat(64)));
    }
}
