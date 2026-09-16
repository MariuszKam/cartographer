package cartographer.perf.macro;

import cartographer.perf.benchmark.BenchmarkExecutionStatus;
import cartographer.perf.benchmark.BenchmarkIterationResult;
import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.workload.RadiusProfile;
import cartographer.perf.workload.RockUpperWorkload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroBaselineRunnerTest {
    private static final String SHA = "a".repeat(40);
    private static final ResultFingerprint FINGERPRINT =
            new ResultFingerprint("b".repeat(64));
    private static final PerformanceEnvironment ENVIRONMENT =
            new PerformanceEnvironment("25", "Test JVM", "Test OS", "1", "amd64", 4, 1024);

    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulRunHashesSaveOnlyAfterBenchmarkAndRendersEvidence() throws Exception {
        Path save = createSave();
        Path output = temporaryDirectory.resolve("baselines");
        List<String> events = new ArrayList<>();

        MacroBaselineRunner runner = new MacroBaselineRunner(
                (plan, operation) -> {
                    events.add("benchmark");
                    return successfulRun();
                },
                (path, workload) -> ignored -> BenchmarkOperationResult.success(FINGERPRINT),
                path -> {
                    assertEquals(List.of("benchmark"), events);
                    events.add("save-fingerprint");
                    return "c".repeat(64);
                },
                new MacroBaselineRenderer()
        );

        MacroBaselineResult result = runner.run(
                save, "ROCK_UPPER_R256", SHA, ENVIRONMENT, output);

        assertEquals(List.of("benchmark", "save-fingerprint"), events);
        assertTrue(Files.isRegularFile(result.reportPath()));
        String report = Files.readString(result.reportPath(), StandardCharsets.UTF_8);
        assertTrue(report.contains("OS filesystem cache state: uncontrolled"));
        assertTrue(report.contains("Save safety: separate gate; not evaluated by this benchmark command"));
        assertFalse(report.contains("Save safety: PASS"));
        assertTrue(report.contains("Min: 1 ns"));
        assertTrue(report.contains("P50: 3 ns"));
        assertTrue(report.contains("P95: 5 ns"));
        assertTrue(report.contains("Max: 5 ns"));
        assertTrue(report.contains("0: 5 ns"));
        assertTrue(report.contains("4: 3 ns"));
        assertEquals("ROCK_UPPER_R256-" + SHA + ".txt",
                result.reportPath().getFileName().toString());
    }

    @Test
    void unsuccessfulRunCannotWriteBaseline() throws Exception {
        Path save = createSave();
        Path output = temporaryDirectory.resolve("baselines");
        MacroBaselineRunner runner = new MacroBaselineRunner(
                (plan, operation) -> new BenchmarkRunResult(
                        plan, List.of(), List.of(), BenchmarkExecutionStatus.MEASURED_FAILURES),
                (path, workload) -> ignored -> BenchmarkOperationResult.success(FINGERPRINT),
                path -> { throw new AssertionError("save fingerprint must not run"); },
                new MacroBaselineRenderer()
        );

        assertThrows(IllegalStateException.class, () -> runner.run(
                save, "ROCK_UPPER_R512", SHA, ENVIRONMENT, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void nondeterministicRunCannotWriteBaseline() throws Exception {
        Path save = createSave();
        Path output = temporaryDirectory.resolve("baselines");
        MacroBaselineRunner runner = new MacroBaselineRunner(
                (plan, operation) -> new BenchmarkRunResult(
                        plan, warmups(), List.of(
                                sample(0, 1, FINGERPRINT),
                                sample(1, 2, new ResultFingerprint("d".repeat(64))),
                                sample(2, 3, FINGERPRINT),
                                sample(3, 4, FINGERPRINT),
                                sample(4, 5, FINGERPRINT)
                        ), BenchmarkExecutionStatus.NONDETERMINISTIC),
                (path, workload) -> ignored -> BenchmarkOperationResult.success(FINGERPRINT),
                path -> { throw new AssertionError("save fingerprint must not run"); },
                new MacroBaselineRenderer()
        );

        assertThrows(IllegalStateException.class, () -> runner.run(
                save, "ROCK_UPPER_R512", SHA, ENVIRONMENT, output));
        assertFalse(Files.exists(output));
    }

    private Path createSave() throws Exception {
        Path save = Files.createTempFile(temporaryDirectory, "world-", ".vcdbs");
        Files.writeString(save, "save", StandardCharsets.UTF_8);
        return save;
    }

    private static BenchmarkRunResult successfulRun() {
        return new BenchmarkRunResult(
                new BenchmarkPlan(new RockUpperWorkload(RadiusProfile.R256),
                        ExecutionMode.JVM_WARM, 2, 5),
                warmups(),
                List.of(
                        sample(0, 5, FINGERPRINT),
                        sample(1, 1, FINGERPRINT),
                        sample(2, 4, FINGERPRINT),
                        sample(3, 2, FINGERPRINT),
                        sample(4, 3, FINGERPRINT)
                ),
                BenchmarkExecutionStatus.SUCCESS
        );
    }

    private static List<BenchmarkIterationResult> warmups() {
        return List.of(sample(0, 99, FINGERPRINT), sample(1, 98, FINGERPRINT));
    }

    private static BenchmarkIterationResult sample(
            int index, long duration, ResultFingerprint fingerprint
    ) {
        return new BenchmarkIterationResult(index, duration, Optional.of(fingerprint),
                Optional.empty(), Optional.empty());
    }
}
