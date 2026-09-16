package cartographer.perf.baseline;

import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.instrumentation.PerformanceInstrumentationSnapshot;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceCounters;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.metrics.PerformanceStageMetrics;
import cartographer.perf.workload.MapWorkload;
import cartographer.perf.workload.RadiusProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceBaselineFactoryTest {
    private static final String SHA = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint("a".repeat(64));
    private static final ResultFingerprint OTHER_FINGERPRINT =
            new ResultFingerprint("b".repeat(64));
    private static final PerformanceEnvironment ENVIRONMENT = new PerformanceEnvironment(
            "25", "Temurin", "Windows", "11", "amd64", 16, 8_000_000_000L
    );

    @Test
    void validRunRetainsIdentityModeEnvironmentSamplesAndFingerprint() {
        BenchmarkRunResult run = run(2, List.of(sample(0, 30), sample(1, 10)),
                List.of(sample(0, 99), sample(1, 20)), BenchmarkExecutionStatus.SUCCESS);

        ReferenceBaseline baseline = create(run);

        assertEquals(SHA.toLowerCase(Locale.ROOT), baseline.gitCommitSha());
        assertEquals("MAP_R128", baseline.workloadId());
        assertEquals("save-42", baseline.saveFingerprint());
        assertEquals(ExecutionMode.JVM_WARM, baseline.executionMode());
        assertEquals(2, baseline.warmupCount());
        assertEquals(2, baseline.measuredIterationCount());
        assertEquals(ENVIRONMENT, baseline.environment());
        assertEquals(FINGERPRINT, baseline.correctnessFingerprint());
        assertEquals(List.of(99L, 20L), baseline.samples().stream()
                .map(ReferenceBaselineSample::wallClockNanoseconds).toList());
        assertEquals(20L, baseline.summary().minWallClockNanoseconds());
        assertEquals(20L, baseline.summary().p50WallClockNanoseconds());
        assertEquals(99L, baseline.summary().p95WallClockNanoseconds());
        assertEquals(99L, baseline.summary().maxWallClockNanoseconds());
    }

    @Test
    void instrumentationPresenceIsRetainedAndAbsenceStaysAbsent() {
        PerformanceInstrumentationSnapshot snapshot = new PerformanceInstrumentationSnapshot(
                new PerformanceStageMetrics(Map.of()),
                new PerformanceCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        );
        BenchmarkIterationResult withInstrumentation = new BenchmarkIterationResult(
                0, 7, Optional.of(FINGERPRINT), Optional.of(snapshot), Optional.empty()
        );
        BenchmarkIterationResult withoutInstrumentation = sample(1, 8);

        ReferenceBaseline baseline = create(run(
                0, List.of(), List.of(withInstrumentation, withoutInstrumentation),
                BenchmarkExecutionStatus.SUCCESS));

        assertEquals(Optional.of(snapshot), baseline.samples().get(0).instrumentation());
        assertEquals(Optional.empty(), baseline.samples().get(1).instrumentation());
    }

    @Test
    void rawOrderIsRetainedWhileSummaryUsesSortedCopy() {
        ReferenceBaseline baseline = create(run(
                0, List.of(), List.of(
                        sample(0, 5), sample(1, 1), sample(2, 4),
                        sample(3, 2), sample(4, 3)
                ), BenchmarkExecutionStatus.SUCCESS));

        assertEquals(List.of(5L, 1L, 4L, 2L, 3L), baseline.samples().stream()
                .map(ReferenceBaselineSample::wallClockNanoseconds).toList());
        assertEquals(1L, baseline.summary().minWallClockNanoseconds());
        assertEquals(3L, baseline.summary().p50WallClockNanoseconds());
        assertEquals(5L, baseline.summary().p95WallClockNanoseconds());
        assertEquals(5L, baseline.summary().maxWallClockNanoseconds());
    }

    @Test
    void nearestRankWorksForOneAndEvenSampleCounts() {
        ReferenceBaseline one = create(run(
                0, List.of(), List.of(sample(0, 7)), BenchmarkExecutionStatus.SUCCESS));
        ReferenceBaseline even = create(run(
                0, List.of(), List.of(
                        sample(0, 4), sample(1, 1), sample(2, 3), sample(3, 2)
                ), BenchmarkExecutionStatus.SUCCESS));

        assertEquals(7L, one.summary().p50WallClockNanoseconds());
        assertEquals(7L, one.summary().p95WallClockNanoseconds());
        assertEquals(2L, even.summary().p50WallClockNanoseconds());
        assertEquals(4L, even.summary().p95WallClockNanoseconds());
    }

    @Test
    void invalidStatusesCannotBecomeBaselines() {
        for (BenchmarkExecutionStatus status : List.of(
                BenchmarkExecutionStatus.NONDETERMINISTIC,
                BenchmarkExecutionStatus.MEASURED_FAILURES,
                BenchmarkExecutionStatus.WARMUP_FAILED
        )) {
            assertThrows(IllegalArgumentException.class, () -> create(run(
                    0, List.of(), List.of(sample(0, 1)), status)));
        }
    }

    @Test
    void mismatchedFingerprintsAndCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> create(run(
                0, List.of(), List.of(
                        sample(0, 1), sample(1, 2, OTHER_FINGERPRINT)
                ), BenchmarkExecutionStatus.SUCCESS)));
        assertThrows(IllegalArgumentException.class, () -> create(run(
                0, List.of(), List.of(sample(0, 1)), BenchmarkExecutionStatus.SUCCESS,
                2)));
        assertThrows(IllegalArgumentException.class, () -> create(run(
                0, List.of(), List.of(), BenchmarkExecutionStatus.SUCCESS)));
    }

    @Test
    void invalidMeasuredIndexesAndWarmupCountAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> create(run(
                0, List.of(), List.of(sample(1, 1)), BenchmarkExecutionStatus.SUCCESS)));
        assertThrows(IllegalArgumentException.class, () -> create(run(
                0, List.of(sample(0, 1)), List.of(sample(0, 1)),
                BenchmarkExecutionStatus.SUCCESS, 0)));
    }

    @Test
    void requiredIdentitiesAndEnvironmentAreValidated() {
        BenchmarkRunResult run = run(0, List.of(), List.of(sample(0, 1)),
                BenchmarkExecutionStatus.SUCCESS);

        assertThrows(IllegalArgumentException.class, () ->
                ReferenceBaselineFactory.from("latest", "save-42", ENVIRONMENT, run));
        assertThrows(IllegalArgumentException.class, () ->
                ReferenceBaselineFactory.from(" ", "save-42", ENVIRONMENT, run));
        assertThrows(IllegalArgumentException.class, () ->
                ReferenceBaselineFactory.from(SHA, " ", ENVIRONMENT, run));
        assertThrows(NullPointerException.class, () ->
                ReferenceBaselineFactory.from(SHA, "save-42", null, run));
    }

    @Test
    void sampleListIsImmutableAndLongDurationsDoNotOverflow() {
        ReferenceBaseline baseline = create(run(
                0, List.of(), List.of(
                        sample(0, Long.MAX_VALUE), sample(1, 0)
                ), BenchmarkExecutionStatus.SUCCESS));

        assertThrows(UnsupportedOperationException.class, () -> baseline.samples().clear());
        assertEquals(0L, baseline.summary().minWallClockNanoseconds());
        assertEquals(Long.MAX_VALUE, baseline.summary().maxWallClockNanoseconds());
        assertTrue(baseline.summary().p95WallClockNanoseconds() >= 0);
    }

    private static ReferenceBaseline create(BenchmarkRunResult run) {
        return ReferenceBaselineFactory.from(SHA, " save-42 ", ENVIRONMENT, run);
    }

    private static BenchmarkRunResult run(
            int warmupCount,
            List<BenchmarkIterationResult> warmups,
            List<BenchmarkIterationResult> measured,
            BenchmarkExecutionStatus status
    ) {
        return run(warmupCount, warmups, measured, status, measured.size());
    }

    private static BenchmarkRunResult run(
            int warmupCount,
            List<BenchmarkIterationResult> warmups,
            List<BenchmarkIterationResult> measured,
            BenchmarkExecutionStatus status,
            int measuredCount
    ) {
        return new BenchmarkRunResult(
                new BenchmarkPlan(
                        new MapWorkload(RadiusProfile.R128),
                        ExecutionMode.JVM_WARM,
                        warmupCount,
                        measuredCount == 0 ? 1 : measuredCount
                ),
                warmups,
                measured,
                status
        );
    }

    private static BenchmarkIterationResult sample(int index, long duration) {
        return sample(index, duration, FINGERPRINT);
    }

    private static BenchmarkIterationResult sample(
            int index,
            long duration,
            ResultFingerprint fingerprint
    ) {
        return new BenchmarkIterationResult(
                index,
                duration,
                Optional.of(fingerprint),
                Optional.empty(),
                Optional.empty()
        );
    }
}
