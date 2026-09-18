package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.macro.Pf18IterationEvidence;
import cartographer.perf.macro.Pf18MacroOperationFactory;
import cartographer.perf.macro.Pf18MacroResolverAccess;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.perf.workload.WorkloadSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Locale;

/** Runs one separate PF-1.8 diagnostic profiling campaign. */
public final class Pf18JfrRunner {
    private static final long MAX_JFR_SIZE_BYTES = 256L * 1024 * 1024;
    private final JfrBenchmarkProfiler profiler;
    private final Pf18JfrAnalyzer analyzer;
    private final Pf18MacroOperationFactory operations;
    private final SaveSafetySnapshotter snapshotter;
    private final SaveSafetyGate safetyGate;

    public Pf18JfrRunner(Pf18MacroOperationFactory operations) {
        this(new JfrBenchmarkProfiler(), new Pf18JfrAnalyzer(), operations,
                new SaveSafetySnapshotter(), new SaveSafetyGate());
    }

    Pf18JfrRunner(JfrBenchmarkProfiler profiler, Pf18JfrAnalyzer analyzer,
                  Pf18MacroOperationFactory operations,
                  SaveSafetySnapshotter snapshotter, SaveSafetyGate safetyGate) {
        this.profiler = Objects.requireNonNull(profiler);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.operations = Objects.requireNonNull(operations);
        this.snapshotter = Objects.requireNonNull(snapshotter);
        this.safetyGate = Objects.requireNonNull(safetyGate);
    }

    public Pf18JfrSummary profile(Path savePath, Path cacheRoot, String gitSha, String workloadId,
                                  Path outputRoot) {
        String normalizedSha = Objects.requireNonNull(gitSha, "gitSha is required")
                .trim().toLowerCase(Locale.ROOT);
        if (!normalizedSha.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitSha must be a full 40-character SHA");
        }
        Path save = savePath.toAbsolutePath().normalize();
        Path cache = cacheRoot.toAbsolutePath().normalize();
        Path output = outputRoot.toAbsolutePath().normalize();
        if (cache.startsWith(save.getParent()) || save.getParent().startsWith(cache)) {
            throw new IllegalArgumentException("cacheRoot must be external to source directory");
        }
        WorkloadSpec workload = Pf18MacroResolverAccess.resolve(workloadId);
        Path recording = output.resolve(workload.id() + ".jfr");
        Path summary = output.resolve(workload.id() + "-jfr-summary.txt");
        if (Files.exists(recording) || Files.exists(summary)) {
            throw new IllegalArgumentException("PF-1.8 JFR evidence already exists");
        }
        try {
            Files.createDirectories(output);
        } catch (java.io.IOException exception) {
            throw new JfrProfilingException("Cannot create JFR output directory", exception);
        }
        SaveSafetySnapshot before = snapshotter.capture(save);
        var operation = operations.create(save, cache, workload);
        JfrRecordingPlan plan = new JfrRecordingPlan(recording, MAX_JFR_SIZE_BYTES,
                "PF-1.8 " + workload.id(), JfrConfiguration.PROFILE);
        profiler.profile(plan, new BenchmarkPlan(workload, ExecutionMode.JVM_WARM, 2, 5),
                ignored -> {
                    Pf18IterationEvidence evidence = operation.execute();
                    return BenchmarkOperationResult.success(composite(evidence));
                });
        SaveSafetySnapshot after = snapshotter.capture(save);
        SaveSafetyResult safety = safetyGate.compare(before, after);
        if (safety.status() != cartographer.perf.safety.SaveSafetyStatus.PASS) {
            throw new JfrProfilingException("source safety failed during JFR profiling: "
                    + safety.violations());
        }
        return analyzer.analyze(recording, summary);
    }

    private static ResultFingerprint composite(Pf18IterationEvidence evidence) {
        try {
            return new ResultFingerprint(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.compositeFingerprint().getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
