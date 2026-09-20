package cartographer.perf.snapshot;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf3RenderSizedValidationReportTest {
    private static final String SHA =
            "0123456789abcdef0123456789abcdef01234567";
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    @Test
    void acceptsCompleteMapSurfaceAndRockEvidence() {
        Pf3RenderSizedValidationReport report =
                new Pf3RenderSizedValidationReport(
                        mapSurface(),
                        passSafety(),
                        List.of(
                                rock(1024, true),
                                rock(2048, true),
                                rock(4096, true)
                        )
                );

        assertTrue(report.accepted(), report.render());
    }

    @Test
    void rejectsRockParityFailureOrMissingRadius() {
        Pf3RenderSizedValidationReport badParity =
                new Pf3RenderSizedValidationReport(
                        mapSurface(),
                        passSafety(),
                        List.of(
                                rock(1024, true),
                                rock(2048, false),
                                rock(4096, true)
                        )
                );
        Pf3RenderSizedValidationReport missingRadius =
                new Pf3RenderSizedValidationReport(
                        mapSurface(),
                        passSafety(),
                        List.of(
                                rock(1024, true),
                                rock(2048, true)
                        )
                );

        assertFalse(badParity.accepted());
        assertFalse(missingRadius.accepted());
    }

    private Pf28SnapshotValidationReport mapSurface() {
        return new Pf28SnapshotValidationReport(
                SHA,
                "revision",
                new Pf28ColdSnapshotCoverage(
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
                ),
                100L,
                Pf18ResourceEvidence.unavailable(),
                passSafety(),
                true,
                List.of(
                        warm(1024),
                        warm(2048),
                        warm(4096)
                )
        );
    }

    private Pf28WarmRenderSample warm(int radius) {
        return new Pf28WarmRenderSample(
                radius,
                10L,
                20L,
                Pf18ResourceEvidence.unavailable(),
                0,
                0,
                100,
                90,
                10,
                100,
                100,
                true,
                FINGERPRINT,
                FINGERPRINT
        );
    }

    private Pf3RockWarmRenderSample rock(
            int radius,
            boolean parity
    ) {
        return new Pf3RockWarmRenderSample(
                radius,
                10L,
                20L,
                Pf18ResourceEvidence.unavailable(),
                parity,
                parity,
                parity,
                FINGERPRINT,
                parity
                        ? FINGERPRINT
                        : new ResultFingerprint(
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                )
        );
    }

    private SaveSafetyResult passSafety() {
        return new SaveSafetyResult(
                SaveSafetyStatus.PASS,
                List.of()
        );
    }
}
