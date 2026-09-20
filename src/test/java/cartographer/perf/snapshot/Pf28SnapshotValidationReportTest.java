package cartographer.perf.snapshot;

import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;
import cartographer.perf.safety.SaveSafetyViolation;
import cartographer.perf.safety.SaveSafetyViolationType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf28SnapshotValidationReportTest {
    private static final String SHA =
            "0123456789abcdef0123456789abcdef01234567";
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            );

    @Test
    void acceptsCompleteColdWarmEvidence() {
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        true,
                        100L,
                        Pf18ResourceEvidence.unavailable(),
                        new SaveSafetyResult(
                                SaveSafetyStatus.PASS,
                                List.of()
                        ),
                        true,
                        List.of(
                                accepted(1024),
                                accepted(2048),
                                accepted(4096)
                        )
                );

        assertTrue(report.accepted(), report.render());
    }

    @Test
    void rejectsAnyWarmSourceConnection() {
        Pf28WarmRenderSample bad =
                new Pf28WarmRenderSample(
                        2048,
                        10L,
                        20L,
                        Pf18ResourceEvidence.unavailable(),
                        1,
                        1,
                        false,
                        true,
                        FINGERPRINT,
                        FINGERPRINT
                );
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        true,
                        100L,
                        Pf18ResourceEvidence.unavailable(),
                        new SaveSafetyResult(
                                SaveSafetyStatus.PASS,
                                List.of()
                        ),
                        true,
                        List.of(
                                accepted(1024),
                                bad,
                                accepted(4096)
                        )
                );

        assertFalse(report.accepted());
    }

    @Test
    void rejectsDuplicateRadiusEvenWhenRequiredSetIsPresent() {
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        true,
                        100L,
                        Pf18ResourceEvidence.unavailable(),
                        new SaveSafetyResult(
                                SaveSafetyStatus.PASS,
                                List.of()
                        ),
                        true,
                        List.of(
                                accepted(1024),
                                accepted(2048),
                                accepted(4096),
                                accepted(4096)
                        )
                );

        assertFalse(report.accepted());
    }

    @Test
    void rejectsSourceMutationOrMissingRadius() {
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        true,
                        100L,
                        Pf18ResourceEvidence.unavailable(),
                        new SaveSafetyResult(
                                SaveSafetyStatus.FAIL,
                                List.of(new SaveSafetyViolation(
                                        SaveSafetyViolationType.SAVE_CONTENT_CHANGED,
                                        Path.of("world.vcdbs")
                                ))
                        ),
                        true,
                        List.of(
                                accepted(1024),
                                accepted(2048)
                        )
                );

        assertFalse(report.accepted());
    }

    private Pf28WarmRenderSample accepted(int radius) {
        return new Pf28WarmRenderSample(
                radius,
                10L,
                20L,
                Pf18ResourceEvidence.unavailable(),
                0,
                0,
                true,
                true,
                FINGERPRINT,
                FINGERPRINT
        );
    }
}
