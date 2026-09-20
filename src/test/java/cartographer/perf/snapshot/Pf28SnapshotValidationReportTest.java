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
                        completeColdCoverage(),
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
    void rejectsIncompleteColdSnapshotCoverage() {
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        new Pf28ColdSnapshotCoverage(
                                100,
                                true,
                                true,
                                false,
                                true,
                                true,
                                true
                        ),
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

        assertFalse(report.accepted());
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
                        100,
                        100,
                        0,
                        100,
                        100,
                        true,
                        FINGERPRINT,
                        FINGERPRINT
                );
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        completeColdCoverage(),
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
    void rejectsIncompleteWarmSnapshotCoverage() {
        Pf28WarmRenderSample bad =
                new Pf28WarmRenderSample(
                        2048,
                        10L,
                        20L,
                        Pf18ResourceEvidence.unavailable(),
                        0,
                        0,
                        true,
                        100,
                        100,
                        0,
                        100,
                        99,
                        true,
                        FINGERPRINT,
                        FINGERPRINT
                );
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        completeColdCoverage(),
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
    void rejectsGeometryOrImageParityFailure() {
        Pf28WarmRenderSample badGeometry =
                new Pf28WarmRenderSample(
                        2048,
                        10L,
                        20L,
                        Pf18ResourceEvidence.unavailable(),
                        0,
                        0,
                        true,
                        100,
                        90,
                        10,
                        100,
                        100,
                        false,
                        FINGERPRINT,
                        FINGERPRINT
                );
        Pf28WarmRenderSample badImage =
                new Pf28WarmRenderSample(
                        2048,
                        10L,
                        20L,
                        Pf18ResourceEvidence.unavailable(),
                        0,
                        0,
                        true,
                        100,
                        90,
                        10,
                        100,
                        100,
                        true,
                        FINGERPRINT,
                        new ResultFingerprint(
                                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                        + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                        )
                );

        assertFalse(reportWith(badGeometry).accepted());
        assertFalse(reportWith(badImage).accepted());
    }

    @Test
    void rejectsFailedRevisionInvalidation() {
        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationReport(
                        SHA,
                        "revision",
                        completeColdCoverage(),
                        100L,
                        Pf18ResourceEvidence.unavailable(),
                        new SaveSafetyResult(
                                SaveSafetyStatus.PASS,
                                List.of()
                        ),
                        false,
                        List.of(
                                accepted(1024),
                                accepted(2048),
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
                        completeColdCoverage(),
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
                        completeColdCoverage(),
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

    private Pf28SnapshotValidationReport reportWith(
            Pf28WarmRenderSample radius2048
    ) {
        return new Pf28SnapshotValidationReport(
                SHA,
                "revision",
                completeColdCoverage(),
                100L,
                Pf18ResourceEvidence.unavailable(),
                new SaveSafetyResult(
                        SaveSafetyStatus.PASS,
                        List.of()
                ),
                true,
                List.of(
                        accepted(1024),
                        radius2048,
                        accepted(4096)
                )
        );
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
    private Pf28ColdSnapshotCoverage completeColdCoverage() {
        return new Pf28ColdSnapshotCoverage(
                100,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

}
