package cartographer.perf.macro;

import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.benchmark.BenchmarkRunner;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.metrics.Pf18ResourceSampler;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.perf.safety.SaveSafetyStatus;
import cartographer.perf.workload.WorkloadSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reviewer-controlled PF-1.8 macro campaign orchestration. */
public final class Pf18MacroRunner {
    public static final int WARMUP_COUNT = 2;
    public static final int MEASURED_COUNT = 5;

    private final Pf18MacroOperationFactory operationFactory;
    private final Pf18ProcessLauncher processLauncher;
    private final BenchmarkRunner benchmarkRunner;
    private final SaveSafetySnapshotter snapshotter;
    private final SaveSafetyGate safetyGate;
    private final PerformanceEnvironment environment;
    private final Pf18ResourceSampler resourceSampler = new Pf18ResourceSampler();

    public Pf18MacroRunner(Pf18MacroOperationFactory operationFactory,
                           Pf18ProcessLauncher processLauncher,
                           PerformanceEnvironment environment) {
        this(operationFactory, processLauncher, new BenchmarkRunner(),
                new SaveSafetySnapshotter(), new SaveSafetyGate(), environment);
    }

    Pf18MacroRunner(Pf18MacroOperationFactory operationFactory,
                    Pf18ProcessLauncher processLauncher,
                    BenchmarkRunner benchmarkRunner,
                    SaveSafetySnapshotter snapshotter,
                    SaveSafetyGate safetyGate,
                    PerformanceEnvironment environment) {
        this.operationFactory = Objects.requireNonNull(operationFactory);
        this.processLauncher = Objects.requireNonNull(processLauncher);
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner);
        this.snapshotter = Objects.requireNonNull(snapshotter);
        this.safetyGate = Objects.requireNonNull(safetyGate);
        this.environment = Objects.requireNonNull(environment);
    }

    public Pf18MacroReport run(Path savePath, Path cacheRoot, String workloadId,
                               String gitSha, ExecutionMode mode, Path outputRoot) {
        Path save = normalizeExisting(savePath, "save");
        Path cache = normalize(cacheRoot, "cacheRoot");
        Path output = normalize(outputRoot, "output");
        Path sourceDirectory = save.getParent();
        if (cache.startsWith(sourceDirectory) || sourceDirectory.startsWith(cache)) {
            throw new IllegalArgumentException(
                    "cacheRoot must be external to the source save directory");
        }
        WorkloadSpec workload = MacroWorkloadResolver.resolve(workloadId);
        String sha = exactSha(gitSha);
        Path campaign = output.resolve(workload.id() + "-" + mode.name().toLowerCase(Locale.ROOT));
        if (Files.exists(campaign)) {
            throw new IllegalArgumentException("PF-1.8 campaign evidence already exists: " + campaign);
        }
        try {
            Files.createDirectories(output);
            Files.createDirectories(campaign);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create PF-1.8 campaign directory", exception);
        }

        SaveSafetySnapshot before = snapshotter.capture(save);
        Pf18IterationEvidence authoritative = operationFactory
                .createAuthoritative(save, workload).execute();
        List<String> cacheEvidence = List.of();
        List<Long> samples = new ArrayList<>();
        List<Pf18ResourceEvidence> resourceEvidence = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        AtomicBoolean cacheHit = new AtomicBoolean(mode != ExecutionMode.CACHE_WARM);
        String preparation;

        if (mode == ExecutionMode.CACHE_WARM) {
            operationFactory.prepareCache(save, cache, workload);
            cacheEvidence = List.copyOf(operationFactory.cacheEvidence(save, cache, workload));
            Pf18IterationEvidence prepared = operationFactory.create(save, cache, workload).execute();
            assertParity(authoritative, prepared, "cache preparation");
            cacheHit.set(prepared.cacheHit());
            if (!cacheHit.get() || cacheEvidence.isEmpty()) {
                failures.add("CACHE_WARM preparation did not prove a compatible cache HIT");
            }
            preparation = "CACHE_WARM; JVM state: warm after explicit preparation";
        } else {
            preparation = mode == ExecutionMode.PROCESS_COLD
                    ? "fresh JVM per measured sample; parent timing includes process startup"
                    : "two unmeasured warmups in one JVM; cache population excluded";
        }

        if (mode == ExecutionMode.PROCESS_COLD) {
            for (int index = 0; index < MEASURED_COUNT; index++) {
                Path childEvidence = campaign.resolve("child-" + index + ".properties");
                try {
                    Pf18ProcessLauncher.Pf18ProcessResult result = processLauncher.launch(
                            save, cache, workload.id(), childEvidence);
                    assertParity(authoritative, result.evidence(), "PROCESS_COLD sample " + index);
                    cacheHit.set(cacheHit.get() && result.evidence().cacheHit());
                    samples.add(result.parentElapsedNanoseconds());
                    resourceEvidence.add(result.resourceEvidence());
                } catch (Exception failure) {
                    failures.add("PROCESS_COLD sample " + index + ": " + failure);
                }
            }
        } else {
            Pf18MacroOperationFactory.Pf18MacroOperation operation =
                    operationFactory.create(save, mode == ExecutionMode.CACHE_WARM ? cache : null,
                            workload);
            java.util.concurrent.atomic.AtomicInteger operationCount =
                    new java.util.concurrent.atomic.AtomicInteger();
            BenchmarkRunResult result = benchmarkRunner.run(
                    new BenchmarkPlan(workload, mode == ExecutionMode.CACHE_WARM
                            ? ExecutionMode.JVM_WARM : mode, WARMUP_COUNT, MEASURED_COUNT),
                    ignored -> {
                        Pf18ResourceSampler.Measured<Pf18IterationEvidence> measured =
                                resourceSampler.measure(operation::execute);
                        Pf18IterationEvidence evidence = measured.result();
                        if (operationCount.getAndIncrement() >= WARMUP_COUNT) {
                            resourceEvidence.add(measured.evidence());
                        }
                        assertParity(authoritative, evidence, "measured iteration");
                        cacheHit.set(cacheHit.get() && evidence.cacheHit());
                        return BenchmarkOperationResult.success(
                                compositeFingerprint(evidence));
                    });
            if (result.status() != cartographer.perf.benchmark.BenchmarkExecutionStatus.SUCCESS) {
                failures.add("benchmark status: " + result.status());
            }
            result.measuredIterations().forEach(iteration -> {
                if (iteration.successful()) {
                    samples.add(iteration.wallClockNanoseconds());
                } else {
                    failures.add(iteration.failure().map(Object::toString)
                            .orElse("incomplete measured iteration"));
                }
            });
        }

        SaveSafetySnapshot after;
        SaveSafetyResult safety;
        try {
            after = snapshotter.capture(save);
            safety = safetyGate.compare(before, after);
        } catch (RuntimeException failure) {
            safety = new SaveSafetyResult(SaveSafetyStatus.FAIL,
                    List.of(new cartographer.perf.safety.SaveSafetyViolation(
                            cartographer.perf.safety.SaveSafetyViolationType.SAVE_CONTENT_CHANGED,
                            save)));
            failures.add("source safety inspection failed: " + failure);
        }
        Path reportPath = campaign.resolve("macro-report.txt");
        Pf18MacroReport report = new Pf18MacroReport(
                sha, sha256(save), workload.id(), workload.family().name(),
                workload.radius().blocks(), mode, preparation, environment,
                mode == ExecutionMode.PROCESS_COLD ? 0 : WARMUP_COUNT,
                MEASURED_COUNT, authoritative.semanticFingerprint(),
                authoritative.imageFingerprint(), resourceEvidence, samples,
                percentile(samples, 0), percentile(samples, 50),
                percentile(samples, 95), percentile(samples, 100), failures,
                safety, cacheHit.get(), cacheEvidence, reportPath);
        try {
            Files.writeString(reportPath, new Pf18MacroReportRenderer().render(report),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write PF-1.8 macro report", exception);
        }
        return report;
    }

    private static void assertParity(Pf18IterationEvidence expected,
                                     Pf18IterationEvidence actual, String phase) {
        if (!expected.semanticFingerprint().equals(actual.semanticFingerprint())
                || !expected.imageFingerprint().equals(actual.imageFingerprint())) {
            throw new IllegalStateException("correctness fingerprint mismatch during " + phase);
        }
    }

    private static ResultFingerprint compositeFingerprint(Pf18IterationEvidence evidence) {
        try {
            return new ResultFingerprint(HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256")
                    .digest(evidence.compositeFingerprint().getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        if (percentile == 0) return sorted.getFirst();
        int index = Math.min(sorted.size() - 1,
                (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static Path normalizeExisting(Path path, String name) {
        Path normalized = normalize(path, name);
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(name + " must be an existing regular file: " + normalized);
        }
        return normalized;
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name + " is required").toAbsolutePath().normalize();
    }

    private static String exactSha(String value) {
        Objects.requireNonNull(value, "gitSha is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitSha must be a full 40-character SHA");
        }
        return normalized;
    }

    private static String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint save: " + path, exception);
        }
    }
}
