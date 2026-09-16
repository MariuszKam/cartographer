package cartographer.perf.report;

import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.baseline.ReferenceBaselineFactory;
import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;
import cartographer.perf.safety.SaveSafetyViolation;
import cartographer.perf.safety.SaveSafetyViolationType;
import cartographer.perf.workload.MapWorkload;
import cartographer.perf.workload.RadiusProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineReportRendererTest {
    private static final String SHA = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
    private static final String SAVE_FINGERPRINT = "save-fingerprint-1";
    private static final ResultFingerprint RESULT_FINGERPRINT =
            new ResultFingerprint("f".repeat(64));
    private static final PerformanceEnvironment ENVIRONMENT = new PerformanceEnvironment(
            "25.0.1", "Temurin", "Windows", "11", "amd64", 16, 8_589_934_592L
    );
    private static final SaveSafetyResult SAFETY_PASS =
            new SaveSafetyResult(SaveSafetyStatus.PASS, List.of());

    @Test
    void factoryRetainsAllBaselineEvidenceAndSafetyPass() {
        BaselineReport report = report(SAFETY_PASS);

        assertEquals("abcdef0123456789abcdef0123456789abcdef01", report.gitCommitSha());
        assertEquals("MAP_R128", report.workloadId());
        assertEquals(SAVE_FINGERPRINT, report.saveFingerprint());
        assertEquals(ExecutionMode.JVM_WARM, report.executionMode());
        assertEquals(ENVIRONMENT, report.environment());
        assertEquals(1, report.warmupCount());
        assertEquals(2, report.measuredIterationCount());
        assertEquals(RESULT_FINGERPRINT, report.correctnessFingerprint());
        assertEquals(100L, report.minWallClockNanoseconds());
        assertEquals(100L, report.p50WallClockNanoseconds());
        assertEquals(200L, report.p95WallClockNanoseconds());
        assertEquals(200L, report.maxWallClockNanoseconds());
        assertEquals(List.of(
                new BaselineReportSample(0, 100),
                new BaselineReportSample(1, 200)
        ), report.samples());
        assertEquals(SaveSafetyStatus.PASS, report.saveSafetyStatus());
        assertTrue(report.saveSafetyViolations().isEmpty());
    }

    @Test
    void goldenOutputIsStableCanonicalNewlineText() {
        String expected = """
                VS Cartographer Performance Baseline
                ====================================

                Identity
                --------
                Git commit: abcdef0123456789abcdef0123456789abcdef01
                Workload: MAP_R128
                Save fingerprint: save-fingerprint-1
                Execution mode: JVM_WARM

                Environment
                -----------
                Java: 25.0.1
                JVM vendor: Temurin
                OS: Windows 11
                Architecture: amd64
                Available processors: 16
                Max heap bytes: 8589934592

                Methodology
                -----------
                Warmups: 1
                Measured iterations: 2

                Correctness
                -----------
                Result fingerprint: ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff

                Wall-clock
                ----------
                Min: 100 ns
                P50: 100 ns
                P95: 200 ns
                Max: 200 ns

                Measured samples
                ----------------
                0: 100 ns
                1: 200 ns

                Save safety
                -----------
                Status: PASS
                Violations: none
                """;

        assertEquals(expected, new BaselineReportRenderer().render(report(SAFETY_PASS)));
    }

    @Test
    void safetyFailureIsRenderedWithoutHidingViolationsOrReorderingThem() {
        SaveSafetyViolation wal = new SaveSafetyViolation(
                SaveSafetyViolationType.WAL_CREATED, Path.of("evidence", "world.vcdbs-wal")
        );
        SaveSafetyViolation content = new SaveSafetyViolation(
                SaveSafetyViolationType.SAVE_CONTENT_CHANGED, Path.of("evidence", "world.vcdbs")
        );
        SaveSafetyResult safety = new SaveSafetyResult(SaveSafetyStatus.FAIL, List.of(wal, content));
        BaselineReport report = report(safety);

        String rendered = new BaselineReportRenderer().render(report);

        assertTrue(rendered.contains("Status: FAIL\nViolations:\n"));
        assertTrue(rendered.contains("- WAL_CREATED: " + wal.path()));
        assertTrue(rendered.contains("- SAVE_CONTENT_CHANGED: " + content.path()));
        assertTrue(rendered.indexOf("WAL_CREATED") < rendered.indexOf("SAVE_CONTENT_CHANGED"));
    }

    @Test
    void rendererIsDeterministicAndUsesOnlyLf() {
        String first = new BaselineReportRenderer().render(report(SAFETY_PASS));
        String second = new BaselineReportRenderer().render(report(SAFETY_PASS));

        assertEquals(first, second);
        assertTrue(!first.contains("\r\n"));
        assertTrue(!first.contains("202"));
        assertEquals(64, RESULT_FINGERPRINT.sha256Hex().length());
    }

    @Test
    void reportCollectionsAreImmutableAndNullInputsAreRejected() {
        BaselineReport report = report(SAFETY_PASS);

        assertThrows(UnsupportedOperationException.class, () -> report.samples().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> report.saveSafetyViolations().clear());
        assertThrows(NullPointerException.class,
                () -> BaselineReportFactory.from(null, SaveSafetyResult.PASS));
        assertThrows(NullPointerException.class,
                () -> BaselineReportFactory.from(baseline(), null));
        assertThrows(NullPointerException.class,
                () -> new BaselineReportRenderer().render(null));
    }

    private static BaselineReport report(SaveSafetyResult safety) {
        return BaselineReportFactory.from(baseline(), safety);
    }

    private static ReferenceBaseline baseline() {
        return ReferenceBaselineFactory.from(
                SHA,
                " " + SAVE_FINGERPRINT + " ",
                ENVIRONMENT,
                new BenchmarkRunResult(
                        new BenchmarkPlan(
                                new MapWorkload(RadiusProfile.R128),
                                ExecutionMode.JVM_WARM,
                                1,
                                2
                        ),
                        List.of(sample(0, 999)),
                        List.of(sample(0, 100), sample(1, 200)),
                        BenchmarkExecutionStatus.SUCCESS
                )
        );
    }

    private static BenchmarkIterationResult sample(int index, long duration) {
        return new BenchmarkIterationResult(
                index,
                duration,
                Optional.of(RESULT_FINGERPRINT),
                Optional.empty(),
                Optional.empty()
        );
    }
}
