package cartographer.perf.macro;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetySnapshotter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18MacroRunnerTest {
    private static final String SHA = "0123456789012345678901234567890123456789";
    private static final PerformanceEnvironment ENVIRONMENT = new PerformanceEnvironment(
            "25", "test-jvm", "test-os", "1", "amd64", 4, 1024);

    @TempDir
    Path temporaryDirectory;

    @Test
    void processColdUsesOneExternalLaunchPerMeasuredSample() throws Exception {
        Path save = save();
        AtomicInteger launches = new AtomicInteger();
        AtomicReference<Pf18CacheMode> mode = new AtomicReference<>();
        Pf18MacroOperationFactory factory = fixedFactory(false, new AtomicInteger());
        Pf18ProcessLauncher launcher = (ignoredSave, ignoredCache, ignoredWorkload, ignoredMode,
                                        ignoredEvidence) -> {
            launches.incrementAndGet();
            mode.set(ignoredMode);
            return new Pf18ProcessLauncher.Pf18ProcessResult(
                    new Pf18IterationEvidence(Optional.of("semantic"), Optional.of("image"),
                            false, "source"), 10, Pf18ResourceEvidence.unavailable());
        };

        Pf18MacroReport report = new Pf18MacroRunner(factory, launcher, ENVIRONMENT).run(
                save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.PROCESS_COLD, temporaryDirectory.resolve("evidence"));

        assertEquals(5, launches.get());
        assertEquals(Pf18CacheMode.DISABLED, mode.get());
        assertEquals(5, report.measuredWallClockNanoseconds().size());
        assertTrue(report.preparation().contains("fresh JVM"));
    }

    @Test
    void cachePreparationIsOutsideMeasuredIterationsAndRequiresEvidence() throws Exception {
        Path save = save();
        List<String> phases = new java.util.ArrayList<>();
        Pf18MacroOperationFactory factory = new Pf18MacroOperationFactory() {
            @Override
            public Pf18MacroOperation create(Path ignoredSave, Path ignoredCache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return () -> {
                    phases.add("operation");
                    return new Pf18IterationEvidence(Optional.of("semantic"), Optional.of("image"),
                            true, "cache hit");
                };
            }

            @Override
            public void prepareCache(Path ignoredSave, Path ignoredCache,
                                     cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                phases.add("prepare");
            }

            @Override
            public List<String> cacheEvidence(Path ignoredSave, Path ignoredCache,
                                              cartographer.perf.workload.WorkloadSpec ignoredWorkload) {
                return List.of("manifest=verified");
            }
        };
        Pf18MacroReport report = new Pf18MacroRunner(factory,
                (a, b, c, d, e) -> { throw new AssertionError("PROCESS_COLD was not requested"); },
                ENVIRONMENT).run(save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.CACHE_WARM, temporaryDirectory.resolve("cache-evidence"));

        assertEquals("prepare", phases.getFirst());
        assertTrue(report.cacheHitVerified());
        assertTrue(report.evidenceIsValid());
        assertEquals(8, phases.size());
    }

    @Test
    void existingCampaignEvidenceIsNotOverwrittenAndShaIsExact() throws Exception {
        Path save = save();
        Path output = temporaryDirectory.resolve("evidence");
        Files.createDirectories(output.resolve("MAP_R128-jvm_warm"));
        Pf18MacroOperationFactory factory = fixedFactory(false, new AtomicInteger());
        Pf18MacroRunner runner = new Pf18MacroRunner(factory,
                (a, b, c, d, e) -> { throw new AssertionError(); }, ENVIRONMENT);

        assertThrows(IllegalArgumentException.class, () -> runner.run(save,
                temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, output));
        assertThrows(IllegalArgumentException.class, () -> runner.run(save,
                temporaryDirectory.resolve("cache"), "MAP_R128", "short",
                ExecutionMode.JVM_WARM, temporaryDirectory.resolve("other")));
    }

    @Test
    void rockCacheWarmIsRejectedBeforeCampaignStateIsCreated() throws Exception {
        Path save = save();
        Path cache = temporaryDirectory.resolve("cache");
        Pf18MacroRunner runner = new Pf18MacroRunner(fixedFactory(false, new AtomicInteger()),
                (a, b, c, d, e) -> { throw new AssertionError(); }, ENVIRONMENT);

        assertThrows(IllegalArgumentException.class, () -> runner.run(save, cache,
                "ROCK_UPPER_R1024", SHA, ExecutionMode.CACHE_WARM,
                temporaryDirectory.resolve("evidence")));
        assertFalse(Files.exists(cache));
    }

    @Test
    void operationFailureStillProducesAfterSafetyAndCannotBeFactual() throws Exception {
        Path save = save();
        Pf18MacroOperationFactory failing = (a, b, c) -> () -> {
            throw new IllegalStateException("declared operation failure");
        };
        Pf18MacroReport report = new Pf18MacroRunner(failing,
                (a, b, c, d, e) -> { throw new AssertionError(); }, ENVIRONMENT).run(
                save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, temporaryDirectory.resolve("failure-evidence"));

        assertTrue(report.afterSafety().isPresent());
        assertFalse(report.evidenceIsValid());
        assertTrue(report.failures().stream().anyMatch(value -> value.contains("campaign failure")));
    }

    @Test
    void outputInsideSourceDirectoryIsRejected() throws Exception {
        Path save = save();
        Pf18MacroRunner runner = new Pf18MacroRunner(fixedFactory(false, new AtomicInteger()),
                (a, b, c, d, e) -> { throw new AssertionError(); }, ENVIRONMENT);

        assertThrows(IllegalArgumentException.class, () -> runner.run(save,
                temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, save.getParent().resolve("evidence")));
    }

    @Test
    void childEvidenceCodecIsStableWithoutGeneratedTimestamp() throws Exception {
        Path first = temporaryDirectory.resolve("child-a.properties");
        Path second = temporaryDirectory.resolve("child-b.properties");
        Map<String, String> values = Map.of("image", "image", "semantic", "semantic",
                "cacheHit", "false", "sourceWork", "source");
        Pf18DeterministicEvidenceCodec.write(first, values);
        Pf18DeterministicEvidenceCodec.write(second, values);
        assertEquals(Files.readString(first, StandardCharsets.UTF_8),
                Files.readString(second, StandardCharsets.UTF_8));
        assertEquals(Optional.of("semantic"), Pf18MacroChildMain.readEvidence(first)
                .semanticFingerprint());
        assertEquals("source", Pf18MacroChildMain.readEvidence(first).sourceWork());
    }

    @Test
    void failedMiddleIterationKeepsLaterEvidenceAtItsDeclaredIndex() throws Exception {
        Path save = save();
        AtomicInteger calls = new AtomicInteger();
        Pf18MacroOperationFactory factory = (a, b, c) -> () -> {
            int call = calls.getAndIncrement();
            if (call == 3) throw new IllegalStateException("middle measured failure");
            return new Pf18IterationEvidence(Optional.of("semantic"), Optional.of("image"),
                    false, "source-" + call);
        };

        Pf18MacroReport report = new Pf18MacroRunner(factory,
                (a, b, c, d, e) -> { throw new AssertionError(); }, ENVIRONMENT).run(
                save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, temporaryDirectory.resolve("indexed-evidence"));

        assertEquals(5, report.measuredEvidence().size());
        assertTrue(!report.measuredEvidence().get(1).successful());
        assertTrue(report.measuredEvidence().get(2).successful());
        assertEquals("source-4", report.measuredEvidence().get(2).sourceWork().orElseThrow());
        assertTrue(report.measuredEvidence().get(1).resourceEvidence().isEmpty());
        assertTrue(report.measuredEvidence().get(2).resourceEvidence().isPresent());
        assertFalse(report.evidenceIsValid());
    }

    @Test
    void zeroSuccessfulSamplesRenderUnavailableTimingAndSafetyInspectionIsInconclusive()
            throws Exception {
        Path save = save();
        Pf18SafetySnapshotProvider snapshots = new Pf18SafetySnapshotProvider() {
            private int calls;
            private final SaveSafetySnapshotter delegate = new SaveSafetySnapshotter();

            @Override
            public cartographer.perf.safety.SaveSafetySnapshot capture(Path path) {
                if (++calls == 2) throw new IllegalStateException("inspection unavailable");
                return delegate.capture(path);
            }
        };
        Pf18MacroOperationFactory failing = (a, b, c) -> () -> {
            throw new IllegalStateException("all samples fail");
        };

        Pf18MacroReport report = new Pf18MacroRunner(failing,
                (a, b, c, d, e) -> { throw new AssertionError(); },
                new cartographer.perf.benchmark.BenchmarkRunner(), snapshots,
                new cartographer.perf.safety.SaveSafetyGate(), ENVIRONMENT).run(
                save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, temporaryDirectory.resolve("unavailable-evidence"));
        String rendered = new Pf18MacroReportRenderer().render(report);

        assertTrue(report.sourceSafety().isEmpty());
        assertTrue(report.sourceSafetyInspectionFailure().isPresent());
        assertTrue(rendered.contains("Min: UNAVAILABLE"));
        assertTrue(rendered.contains("INCONCLUSIVE (inspection unavailable)"));
        assertTrue(!rendered.contains("SAVE_CONTENT_CHANGED"));
        assertFalse(report.evidenceIsValid());
    }

    private Pf18MacroOperationFactory fixedFactory(boolean cacheHit, AtomicInteger calls) {
        return (save, cache, workload) -> () -> {
            calls.incrementAndGet();
            return new Pf18IterationEvidence(Optional.of("semantic"), Optional.of("image"),
                    cache != null && cacheHit, "fixed source work");
        };
    }

    private Path save() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Path path = source.resolve("world.vcdbs");
        Files.write(path, new byte[]{1, 2, 3});
        return path;
    }
}
