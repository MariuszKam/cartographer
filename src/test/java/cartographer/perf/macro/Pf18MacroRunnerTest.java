package cartographer.perf.macro;

import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
        Pf18MacroOperationFactory factory = fixedFactory(false, new AtomicInteger());
        Pf18ProcessLauncher launcher = (ignoredSave, ignoredCache, ignoredWorkload, ignoredEvidence) -> {
            launches.incrementAndGet();
            return new Pf18ProcessLauncher.Pf18ProcessResult(
                    new Pf18IterationEvidence(Optional.of("semantic"), Optional.of("image"),
                            false, "source"), 10);
        };

        Pf18MacroReport report = new Pf18MacroRunner(factory, launcher, ENVIRONMENT).run(
                save, temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.PROCESS_COLD, temporaryDirectory.resolve("evidence"));

        assertEquals(5, launches.get());
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
                (a, b, c, d) -> { throw new AssertionError("PROCESS_COLD was not requested"); },
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
                (a, b, c, d) -> { throw new AssertionError(); }, ENVIRONMENT);

        assertThrows(IllegalArgumentException.class, () -> runner.run(save,
                temporaryDirectory.resolve("cache"), "MAP_R128", SHA,
                ExecutionMode.JVM_WARM, output));
        assertThrows(IllegalArgumentException.class, () -> runner.run(save,
                temporaryDirectory.resolve("cache"), "MAP_R128", "short",
                ExecutionMode.JVM_WARM, temporaryDirectory.resolve("other")));
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
