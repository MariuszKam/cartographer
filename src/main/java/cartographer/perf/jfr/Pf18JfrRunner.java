package cartographer.perf.jfr;

import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.macro.Pf18IterationEvidence;
import cartographer.perf.macro.Pf18MacroOperationFactory;
import cartographer.perf.macro.Pf18MacroResolverAccess;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.perf.safety.SaveSafetyStatus;
import cartographer.perf.workload.WorkloadFamily;
import cartographer.perf.workload.WorkloadSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

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
        String sha = Objects.requireNonNull(gitSha, "gitSha is required")
                .trim().toLowerCase(Locale.ROOT);
        if (!sha.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitSha must be a full 40-character SHA");
        }
        Path save = savePath.toAbsolutePath().normalize();
        Path sourceDirectory = save.getParent();
        Path cache = cacheRoot.toAbsolutePath().normalize();
        Path output = outputRoot.toAbsolutePath().normalize();
        if (cache.equals(save) || output.equals(save)
                || cache.startsWith(sourceDirectory) || sourceDirectory.startsWith(cache)
                || output.startsWith(sourceDirectory) || sourceDirectory.startsWith(output)
                || cache.startsWith(output) || output.startsWith(cache)) {
            throw new IllegalArgumentException(
                    "JFR cache/output roots must be external, distinct, and outside the source directory");
        }
        WorkloadSpec workload = Pf18MacroResolverAccess.resolve(workloadId);
        Path recording = output.resolve(workload.id() + ".jfr");
        Path summary = output.resolve(workload.id() + "-jfr-summary.txt");
        if (Files.exists(recording) || Files.exists(summary)) {
            throw new IllegalArgumentException("PF-1.8 JFR evidence already exists");
        }
        Path campaignCache = workload.family() == WorkloadFamily.MAP
                ? cache.resolve("pf18-jfr-" + sha + "-" + workload.id()) : null;
        if (campaignCache != null && Files.exists(campaignCache)) {
            throw new IllegalArgumentException("PF-1.8 JFR cache already exists: " + campaignCache);
        }
        try {
            Files.createDirectories(output);
            if (campaignCache != null) Files.createDirectories(campaignCache);
        } catch (java.io.IOException exception) {
            throw new JfrProfilingException("Cannot create PF-1.8 JFR evidence namespace", exception);
        }

        SaveSafetySnapshot before = snapshotter.capture(save);
        SaveSafetySnapshot after = null;
        SaveSafetyResult safety = null;
        RuntimeException afterFailure = null;
        Throwable primaryFailure = null;
        Pf18IterationEvidence authoritative = null;
        String profileState = workload.family() == WorkloadFamily.MAP
                ? "CACHE_WARM" : "PROCESS_SOURCE_AUTHORITATIVE";
        try {
            authoritative = operations.createAuthoritative(save, workload).execute();
            Pf18MacroOperationFactory.Pf18MacroOperation operation;
            if (workload.family() == WorkloadFamily.MAP) {
                operations.prepareCache(save, campaignCache, workload);
                if (operations.cacheEvidence(save, campaignCache, workload).isEmpty()) {
                    throw new JfrProfilingException("MAP JFR cache preparation produced no evidence");
                }
                Pf18IterationEvidence prepared = operations.create(save, campaignCache, workload)
                        .execute();
                assertParity(authoritative, prepared, "MAP JFR cache preflight");
                if (!prepared.cacheHit()) {
                    throw new JfrProfilingException("MAP JFR cache preflight did not prove HIT");
                }
                operation = operations.create(save, campaignCache, workload);
            } else {
                operation = operations.create(save, null, workload);
            }
            Pf18IterationEvidence expected = authoritative;
            JfrRecordingResult profiling = profiler.profile(
                    new JfrRecordingPlan(recording, MAX_JFR_SIZE_BYTES,
                            "PF-1.8 " + workload.id(), JfrConfiguration.PROFILE),
                    new BenchmarkPlan(workload, ExecutionMode.JVM_WARM, 2, 5),
                    ignored -> {
                        Pf18IterationEvidence evidence = operation.execute();
                        assertParity(expected, evidence, "JFR measured operation");
                        return BenchmarkOperationResult.success(composite(evidence));
                    });
            BenchmarkRunResult result = profiling.benchmarkResult();
            ResultFingerprint expectedFingerprint = composite(expected);
            if (result.status() != cartographer.perf.benchmark.BenchmarkExecutionStatus.SUCCESS
                    || result.measuredIterations().size() != 5
                    || result.hasMissingMeasuredFingerprints()
                    || !result.hasConsistentFingerprints()
                    || result.measuredIterations().stream().anyMatch(iteration ->
                    iteration.fingerprint().isEmpty()
                            || !iteration.fingerprint().orElseThrow().equals(expectedFingerprint))) {
                throw new JfrProfilingException(
                        "JFR profiling result is incomplete, non-deterministic, or incorrect: "
                                + result.status());
            }
        } catch (RuntimeException | OutOfMemoryError failure) {
            primaryFailure = failure;
        } finally {
            try {
                after = snapshotter.capture(save);
                safety = safetyGate.compare(before, after);
            } catch (RuntimeException failure) {
                afterFailure = failure;
            }
        }
        if (primaryFailure != null && afterFailure != null) primaryFailure.addSuppressed(afterFailure);
        if (primaryFailure != null) throw propagate(primaryFailure);
        if (afterFailure != null) throw new JfrProfilingException(
                "JFR source-safety AFTER inspection failed", afterFailure);
        if (safety == null || safety.status() != SaveSafetyStatus.PASS) {
            throw new JfrProfilingException("JFR source safety failed: "
                    + (safety == null ? "UNAVAILABLE" : safety.violations()));
        }
        Pf18JfrCampaignIdentity identity = new Pf18JfrCampaignIdentity(
                sha, workload.id(), workload.family().name(), workload.radius().blocks(),
                profileState, JfrConfiguration.PROFILE.name(), MAX_JFR_SIZE_BYTES, save,
                safety.status().name(), authoritative.semanticFingerprint(), authoritative.imageFingerprint());
        return analyzer.analyze(recording, summary, identity);
    }

    private static void assertParity(Pf18IterationEvidence expected,
                                     Pf18IterationEvidence actual, String phase) {
        if (!expected.semanticFingerprint().equals(actual.semanticFingerprint())
                || !expected.imageFingerprint().equals(actual.imageFingerprint())) {
            throw new JfrProfilingException("correctness fingerprint mismatch during " + phase);
        }
    }

    private static ResultFingerprint composite(Pf18IterationEvidence evidence) {
        try {
            return new ResultFingerprint(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(evidence.compositeFingerprint().getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof OutOfMemoryError outOfMemory) {
            return new JfrProfilingException(
                    "JFR profiling encountered OutOfMemoryError; failed sample is scalability evidence",
                    outOfMemory);
        }
        return new JfrProfilingException("JFR profiling failed", failure);
    }
}
