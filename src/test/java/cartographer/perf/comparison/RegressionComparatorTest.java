package cartographer.perf.comparison;

import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.baseline.ReferenceBaselineFactory;
import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.workload.MapWorkload;
import cartographer.perf.workload.RadiusProfile;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionComparatorTest {
    private static final String BASELINE_SHA = "1".repeat(40);
    private static final String CANDIDATE_SHA = "2".repeat(40);
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint("a".repeat(64));
    private static final ResultFingerprint OTHER_FINGERPRINT =
            new ResultFingerprint("b".repeat(64));
    private static final PerformanceEnvironment ENVIRONMENT = environment(
            "25", "Temurin", "Windows", "11", "amd64", 16, 8_000
    );

    @Test
    void identicalComparableEvidencePassesAndExposesAllDeltas() {
        RegressionComparison comparison = new RegressionComparator().compare(
                baseline(List.of(100, 200, 300, 400, 500), FINGERPRINT),
                candidate(List.of(100, 200, 300, 400, 500), FINGERPRINT)
        );

        assertEquals(RegressionComparisonStatus.PASS, comparison.status());
        assertEquals(Optional.of(true), comparison.fingerprintMatch());
        assertEquals(BigInteger.ZERO, comparison.p50().orElseThrow().signedDeltaNanoseconds());
        assertEquals("0.0000", comparison.p50().orElseThrow()
                .relativeChangePercent().orElseThrow().toPlainString());
        assertTrue(comparison.min().isPresent());
        assertTrue(comparison.p95().isPresent());
        assertTrue(comparison.max().isPresent());
    }

    @Test
    void fasterAndSlowerCandidatesUseCandidateMinusBaselineSign() {
        RegressionComparison faster = new RegressionComparator().compare(
                baseline(List.of(100, 200, 300), FINGERPRINT),
                candidate(List.of(80, 160, 240), FINGERPRINT)
        );
        RegressionComparison slower = new RegressionComparator().compare(
                baseline(List.of(100, 200, 300), FINGERPRINT),
                candidate(List.of(120, 240, 360), FINGERPRINT)
        );

        assertEquals(-20L, faster.min().orElseThrow().signedDeltaNanoseconds().longValueExact());
        assertEquals("-20.0000", faster.min().orElseThrow()
                .relativeChangePercent().orElseThrow().toPlainString());
        assertEquals(20L, slower.min().orElseThrow().signedDeltaNanoseconds().longValueExact());
        assertEquals("20.0000", slower.min().orElseThrow()
                .relativeChangePercent().orElseThrow().toPlainString());
    }

    @Test
    void eachSummaryMetricDeltaUsesItsOwnValues() {
        RegressionComparison comparison = new RegressionComparator().compare(
                baseline(List.of(100, 200, 300, 400, 500), FINGERPRINT),
                candidate(List.of(80, 180, 280, 380, 480), FINGERPRINT)
        );

        assertEquals(-20L, comparison.min().orElseThrow().signedDeltaNanoseconds().longValueExact());
        assertEquals(-20L, comparison.p50().orElseThrow().signedDeltaNanoseconds().longValueExact());
        assertEquals(-20L, comparison.p95().orElseThrow().signedDeltaNanoseconds().longValueExact());
        assertEquals(-20L, comparison.max().orElseThrow().signedDeltaNanoseconds().longValueExact());
    }

    @Test
    void zeroBaselineHasNoRelativePercentage() {
        RegressionComparison comparison = new RegressionComparator().compare(
                baseline(List.of(0, 0), FINGERPRINT),
                candidate(List.of(1, 2), FINGERPRINT)
        );

        assertEquals(1L, comparison.min().orElseThrow().candidateNanoseconds());
        assertEquals(BigInteger.ONE, comparison.min().orElseThrow().signedDeltaNanoseconds());
        assertEquals(Optional.empty(), comparison.min().orElseThrow().relativeChangePercent());
    }

    @Test
    void fingerprintMismatchFailsWithoutPerformanceDeltas() {
        RegressionComparison comparison = new RegressionComparator().compare(
                baseline(List.of(100), FINGERPRINT),
                candidate(List.of(1), OTHER_FINGERPRINT)
        );

        assertEquals(RegressionComparisonStatus.FAIL, comparison.status());
        assertEquals(Optional.of(false), comparison.fingerprintMatch());
        assertTrue(comparison.min().isEmpty());
        assertTrue(comparison.inconclusiveReasons().isEmpty());
    }

    @Test
    void semanticAndMethodologyMismatchesAreInconclusiveInFixedOrder() {
        ReferenceBaseline baseline = baseline(List.of(100), FINGERPRINT);
        CandidateMeasurement candidate = candidate(
                List.of(100), FINGERPRINT, new MapWorkload(RadiusProfile.R256),
                environment("26", "Other", "Linux", "1", "arm64", 8, 9_000),
                ExecutionMode.CACHE_WARM, 2
        );

        RegressionComparison comparison = new RegressionComparator().compare(baseline, candidate);

        assertEquals(RegressionComparisonStatus.INCONCLUSIVE, comparison.status());
        assertEquals(List.of(
                RegressionInconclusiveReason.WORKLOAD_MISMATCH,
                RegressionInconclusiveReason.SAVE_FINGERPRINT_MISMATCH,
                RegressionInconclusiveReason.EXECUTION_MODE_MISMATCH,
                RegressionInconclusiveReason.ENVIRONMENT_MISMATCH,
                RegressionInconclusiveReason.WARMUP_COUNT_MISMATCH
        ), comparison.inconclusiveReasons());
        assertTrue(comparison.fingerprintMatch().isEmpty());
        assertTrue(comparison.min().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> comparison.inconclusiveReasons().clear());
    }

    @Test
    void everyEnvironmentFieldIsPartOfExactComparability() {
        List<PerformanceEnvironment> variants = List.of(
                environment("26", "Temurin", "Windows", "11", "amd64", 16, 8_000),
                environment("25", "Other", "Windows", "11", "amd64", 16, 8_000),
                environment("25", "Temurin", "Linux", "11", "amd64", 16, 8_000),
                environment("25", "Temurin", "Windows", "10", "amd64", 16, 8_000),
                environment("25", "Temurin", "Windows", "11", "arm64", 16, 8_000),
                environment("25", "Temurin", "Windows", "11", "amd64", 8, 8_000),
                environment("25", "Temurin", "Windows", "11", "amd64", 16, 9_000)
        );
        for (PerformanceEnvironment variant : variants) {
            RegressionComparison comparison = new RegressionComparator().compare(
                    baseline(List.of(1), FINGERPRINT),
                    candidate(List.of(1), FINGERPRINT, new MapWorkload(RadiusProfile.R128),
                            variant, ExecutionMode.JVM_WARM, 1)
            );
            assertEquals(RegressionComparisonStatus.INCONCLUSIVE, comparison.status());
            assertEquals(List.of(RegressionInconclusiveReason.ENVIRONMENT_MISMATCH),
                    comparison.inconclusiveReasons());
        }
    }

    @Test
    void measuredCountMismatchIsInconclusive() {
        RegressionComparison comparison = new RegressionComparator().compare(
                baseline(List.of(1, 2), FINGERPRINT),
                candidate(List.of(1), FINGERPRINT)
        );

        assertEquals(RegressionComparisonStatus.INCONCLUSIVE, comparison.status());
        assertEquals(List.of(RegressionInconclusiveReason.MEASURED_ITERATION_COUNT_MISMATCH),
                comparison.inconclusiveReasons());
    }

    @Test
    void candidateFactoryValidatesStatusFingerprintsIndexesAndRetainsRawOrder() {
        CandidateMeasurement valid = candidate(List.of(5, 1, 4, 2, 3), FINGERPRINT);
        assertEquals(List.of(5L, 1L, 4L, 2L, 3L), valid.samples().stream()
                .map(CandidateMeasurementSample::wallClockNanoseconds).toList());
        assertEquals(3L, valid.summary().p50WallClockNanoseconds());
        assertEquals(5L, valid.summary().p95WallClockNanoseconds());
        assertThrows(UnsupportedOperationException.class, () -> valid.samples().clear());

        assertThrows(IllegalArgumentException.class, () -> candidateWithStatus(
                BenchmarkExecutionStatus.MEASURED_FAILURES));
        assertThrows(IllegalArgumentException.class, () -> candidateWithFingerprintMismatch());
        assertThrows(IllegalArgumentException.class, () -> candidateWithIndex(1));
        assertThrows(IllegalArgumentException.class, () ->
                CandidateMeasurementFactory.from("latest", "save", ENVIRONMENT,
                        successfulRun(List.of(sample(0, 1, FINGERPRINT)), 1)));
    }

    @Test
    void candidateFactoryRejectsBlankSaveIdentityAndEmptyMeasuredEvidence() {
        BenchmarkRunResult run = new BenchmarkRunResult(
                new BenchmarkPlan(new MapWorkload(RadiusProfile.R128),
                        ExecutionMode.JVM_WARM, 0, 1),
                List.of(), List.of(), BenchmarkExecutionStatus.SUCCESS
        );
        assertThrows(IllegalArgumentException.class, () ->
                CandidateMeasurementFactory.from(CANDIDATE_SHA, " ", ENVIRONMENT, run));
        assertThrows(IllegalArgumentException.class, () ->
                CandidateMeasurementFactory.from(CANDIDATE_SHA, "save", ENVIRONMENT, run));
    }

    @Test
    void relativeScaleAndLongOverflowBehaviorAreDeterministic() {
        RegressionComparison rounded = new RegressionComparator().compare(
                baseline(List.of(3), FINGERPRINT),
                candidate(List.of(4), FINGERPRINT)
        );
        assertEquals("33.3333", rounded.min().orElseThrow()
                .relativeChangePercent().orElseThrow().toPlainString());

        RegressionComparison overflowSafe = new RegressionComparator().compare(
                baseline(List.of(Long.MAX_VALUE), FINGERPRINT),
                candidate(List.of(0), FINGERPRINT)
        );
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).negate(),
                overflowSafe.min().orElseThrow().signedDeltaNanoseconds());
    }

    private static ReferenceBaseline baseline(List<Integer> durations, ResultFingerprint fingerprint) {
        return ReferenceBaselineFactory.from(
                BASELINE_SHA, "save-1", ENVIRONMENT,
                successfulRun(durations.stream().map(Integer::longValue).toList(), 1, fingerprint)
        );
    }

    private static CandidateMeasurement candidate(List<Integer> durations, ResultFingerprint fingerprint) {
        return candidate(durations, fingerprint, new MapWorkload(RadiusProfile.R128),
                ENVIRONMENT, ExecutionMode.JVM_WARM, 1);
    }

    private static CandidateMeasurement candidate(
            List<Integer> durations,
            ResultFingerprint fingerprint,
            MapWorkload workload,
            PerformanceEnvironment environment,
            ExecutionMode mode,
            int warmupCount
    ) {
        return CandidateMeasurementFactory.from(
                CANDIDATE_SHA,
                "save-1",
                environment,
                new BenchmarkRunResult(
                        new BenchmarkPlan(workload, mode, warmupCount, durations.size()),
                        warmups(warmupCount, fingerprint),
                        samples(durations, fingerprint),
                        BenchmarkExecutionStatus.SUCCESS
                )
        );
    }

    private static CandidateMeasurement candidateWithStatus(BenchmarkExecutionStatus status) {
        return CandidateMeasurementFactory.from(
                CANDIDATE_SHA, "save-1", ENVIRONMENT,
                new BenchmarkRunResult(
                        new BenchmarkPlan(new MapWorkload(RadiusProfile.R128),
                                ExecutionMode.JVM_WARM, 0, 1),
                        List.of(), List.of(sample(0, 1, FINGERPRINT)), status
                )
        );
    }

    private static CandidateMeasurement candidateWithFingerprintMismatch() {
        return CandidateMeasurementFactory.from(
                CANDIDATE_SHA, "save-1", ENVIRONMENT,
                successfulRun(List.of(
                        sample(0, 1, FINGERPRINT), sample(1, 2, OTHER_FINGERPRINT)
                ), 2)
        );
    }

    private static CandidateMeasurement candidateWithIndex(int index) {
        return CandidateMeasurementFactory.from(
                CANDIDATE_SHA, "save-1", ENVIRONMENT,
                successfulRun(List.of(sample(index, 1, FINGERPRINT)), 1)
        );
    }

    private static BenchmarkRunResult successfulRun(List<Long> durations, int warmupCount) {
        return successfulRun(durations, warmupCount, FINGERPRINT);
    }

    private static BenchmarkRunResult successfulRun(
            List<Long> durations, int warmupCount, ResultFingerprint fingerprint
    ) {
        return new BenchmarkRunResult(
                new BenchmarkPlan(new MapWorkload(RadiusProfile.R128),
                        ExecutionMode.JVM_WARM, warmupCount, durations.size()),
                warmups(warmupCount, fingerprint),
                samples(durations, fingerprint),
                BenchmarkExecutionStatus.SUCCESS
        );
    }

    private static List<BenchmarkIterationResult> samples(
            List<Integer> durations, ResultFingerprint fingerprint
    ) {
        List<BenchmarkIterationResult> samples = new ArrayList<>();
        for (int index = 0; index < durations.size(); index++) {
            samples.add(sample(index, durations.get(index), fingerprint));
        }
        return samples;
    }

    private static List<BenchmarkIterationResult> warmups(
            int count, ResultFingerprint fingerprint
    ) {
        List<BenchmarkIterationResult> warmups = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            warmups.add(sample(index, 99, fingerprint));
        }
        return warmups;
    }

    private static BenchmarkIterationResult sample(
            int index, long duration, ResultFingerprint fingerprint
    ) {
        return new BenchmarkIterationResult(
                index, duration, Optional.of(fingerprint), Optional.empty(), Optional.empty()
        );
    }

    private static PerformanceEnvironment environment(
            String javaVersion,
            String vendor,
            String osName,
            String osVersion,
            String architecture,
            int processors,
            long heap
    ) {
        return new PerformanceEnvironment(
                javaVersion, vendor, osName, osVersion, architecture, processors, heap
        );
    }
}
