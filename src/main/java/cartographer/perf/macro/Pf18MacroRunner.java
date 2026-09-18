package cartographer.perf.macro;

import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.metrics.Pf18ResourceSampler;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.perf.workload.WorkloadFamily;
import cartographer.perf.workload.WorkloadSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Reviewer-controlled PF-1.8 macro campaign orchestration. */
public final class Pf18MacroRunner {
    public static final int WARMUP_COUNT = 2;
    public static final int MEASURED_COUNT = 5;

    private final Pf18MacroOperationFactory operationFactory;
    private final Pf18ProcessLauncher processLauncher;
    private final cartographer.perf.benchmark.BenchmarkRunner benchmarkRunner;
    private final Pf18SafetySnapshotProvider snapshotProvider;
    private final SaveSafetyGate safetyGate;
    private final PerformanceEnvironment environment;
    private final Pf18ResourceSampler resourceSampler = new Pf18ResourceSampler();

    public Pf18MacroRunner(Pf18MacroOperationFactory operationFactory,
                           Pf18ProcessLauncher processLauncher,
                           PerformanceEnvironment environment) {
        this(operationFactory, processLauncher, new cartographer.perf.benchmark.BenchmarkRunner(),
                new SaveSafetySnapshotter()::capture, new SaveSafetyGate(), environment);
    }

    Pf18MacroRunner(Pf18MacroOperationFactory operationFactory,
                    Pf18ProcessLauncher processLauncher,
                    cartographer.perf.benchmark.BenchmarkRunner benchmarkRunner,
                    SaveSafetySnapshotter snapshotter,
                    SaveSafetyGate safetyGate,
                    PerformanceEnvironment environment) {
        this(operationFactory, processLauncher, benchmarkRunner, snapshotter::capture,
                safetyGate, environment);
    }

    Pf18MacroRunner(Pf18MacroOperationFactory operationFactory,
                    Pf18ProcessLauncher processLauncher,
                    cartographer.perf.benchmark.BenchmarkRunner benchmarkRunner,
                    Pf18SafetySnapshotProvider snapshotProvider,
                    SaveSafetyGate safetyGate,
                    PerformanceEnvironment environment) {
        this.operationFactory = Objects.requireNonNull(operationFactory);
        this.processLauncher = Objects.requireNonNull(processLauncher);
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner);
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider);
        this.safetyGate = Objects.requireNonNull(safetyGate);
        this.environment = Objects.requireNonNull(environment);
    }

    public Pf18MacroReport run(Path savePath, Path cacheRoot, String workloadId,
                               String gitSha, ExecutionMode mode, Path outputRoot) {
        Path save = normalizeExisting(savePath, "save");
        Path cache = normalize(cacheRoot, "cacheRoot");
        Path output = normalize(outputRoot, "output");
        Path sourceDirectory = save.getParent();
        if (cache.equals(save) || cache.startsWith(sourceDirectory)
                || sourceDirectory.startsWith(cache)) {
            throw new IllegalArgumentException(
                    "cacheRoot must be external to the source save directory");
        }
        WorkloadSpec workload = MacroWorkloadResolver.resolve(workloadId);
        String sha = exactSha(gitSha);
        if (mode == ExecutionMode.CACHE_WARM && workload.family() != WorkloadFamily.MAP) {
            throw new IllegalArgumentException(
                    "CACHE_WARM is supported only for MAP workloads; ROCK is source-only");
        }
        if (output.equals(save) || output.startsWith(sourceDirectory)
                || sourceDirectory.startsWith(output)) {
            throw new IllegalArgumentException(
                    "outputRoot must be external to the source save directory");
        }
        Path campaign = output.resolve(workload.id() + "-" + mode.name().toLowerCase(Locale.ROOT));
        Path campaignCache = cache.resolve("pf18-" + sha + "-" + workload.id()
                + "-" + mode.name().toLowerCase(Locale.ROOT));
        if (Files.exists(campaign)) {
            throw new IllegalArgumentException("PF-1.8 campaign evidence already exists: " + campaign);
        }
        if (mode == ExecutionMode.CACHE_WARM && Files.exists(campaignCache)) {
            throw new IllegalArgumentException(
                    "PF-1.8 campaign cache already exists: " + campaignCache);
        }
        try {
            Files.createDirectories(output);
            Files.createDirectories(campaign);
            if (mode == ExecutionMode.CACHE_WARM) Files.createDirectories(campaignCache);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create PF-1.8 campaign directory", exception);
        }

        SaveSafetySnapshot before = snapshotProvider.capture(save);
        Pf18IterationEvidence authoritative = null;
        List<String> cacheEvidence = List.of();
        List<String> failures = new ArrayList<>();
        List<Pf18MeasuredIterationEvidence> indexedEvidence = new ArrayList<>(
                Collections.nCopies(MEASURED_COUNT, null));
        String preparation = "campaign did not reach preparation";

        try {
            authoritative = operationFactory.createAuthoritative(save, workload).execute();
            if (mode == ExecutionMode.CACHE_WARM) {
                operationFactory.prepareCache(save, campaignCache, workload);
                cacheEvidence = List.copyOf(operationFactory.cacheEvidence(
                        save, campaignCache, workload));
                Pf18IterationEvidence prepared = operationFactory.create(
                        save, campaignCache, workload).execute();
                assertParity(authoritative, prepared, "cache preparation");
                if (!prepared.cacheHit() || cacheEvidence.isEmpty()) {
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
                                save, cache, workload.id(), Pf18CacheMode.DISABLED, childEvidence);
                        assertParity(authoritative, result.evidence(), "PROCESS_COLD sample " + index);
                        indexedEvidence.set(index, successfulEvidence(index,
                                result.parentElapsedNanoseconds(), result.evidence(),
                                result.resourceEvidence()));
                    } catch (Exception failure) {
                        failures.add("PROCESS_COLD sample " + index + ": " + failure);
                        indexedEvidence.set(index, failedEvidence(index, failure, Optional.empty(),
                                Optional.empty(), Optional.empty()));
                    }
                }
            } else {
                Pf18MacroOperationFactory.Pf18MacroOperation operation = operationFactory.create(
                        save, mode == ExecutionMode.CACHE_WARM ? campaignCache : null, workload);
                AtomicInteger invocation = new AtomicInteger();
                Pf18IterationEvidence expectedAuthoritative = authoritative;
                BenchmarkRunResult result = benchmarkRunner.run(
                        new BenchmarkPlan(workload, ExecutionMode.JVM_WARM,
                                WARMUP_COUNT, MEASURED_COUNT),
                        ignored -> {
                            int invocationIndex = invocation.getAndIncrement();
                            int measuredIndex = invocationIndex - WARMUP_COUNT;
                            Pf18ResourceSampler.Measured<Pf18IterationEvidence> measured = null;
                            try {
                                measured = resourceSampler.measure(operation::execute);
                                Pf18IterationEvidence evidence = measured.result();
                                assertParity(expectedAuthoritative, evidence, "measured iteration");
                                if (mode == ExecutionMode.CACHE_WARM && !evidence.cacheHit()) {
                                    throw new IllegalStateException(
                                            "CACHE_WARM measured iteration did not prove HIT");
                                }
                                if (measuredIndex >= 0 && measuredIndex < MEASURED_COUNT) {
                                    indexedEvidence.set(measuredIndex, successfulEvidence(measuredIndex,
                                            0, evidence, measured.evidence()));
                                }
                                return BenchmarkOperationResult.success(compositeFingerprint(evidence));
                            } catch (RuntimeException failure) {
                                if (measuredIndex >= 0 && measuredIndex < MEASURED_COUNT) {
                                    Optional<Pf18IterationEvidence> observed = measured == null
                                            ? Optional.empty() : Optional.of(measured.result());
                                    Optional<Pf18ResourceEvidence> resources = measured == null
                                            ? Optional.empty() : Optional.of(measured.evidence());
                                    indexedEvidence.set(measuredIndex, failedEvidence(measuredIndex,
                                            failure, observed.map(Pf18IterationEvidence::cacheHit),
                                            observed.map(Pf18IterationEvidence::sourceWork), resources));
                                }
                                throw failure;
                            }
                        });
                if (result.status() != cartographer.perf.benchmark.BenchmarkExecutionStatus.SUCCESS) {
                    failures.add("benchmark status: " + result.status());
                }
                for (var iteration : result.measuredIterations()) {
                    int index = iteration.iterationIndex();
                    Pf18MeasuredIterationEvidence observed = indexedEvidence.get(index);
                    if (iteration.successful() && observed != null && observed.successful()) {
                        indexedEvidence.set(index, new Pf18MeasuredIterationEvidence(index,
                                OptionalLong.of(iteration.wallClockNanoseconds()), true,
                                observed.cacheHit(), observed.sourceWork(), Optional.empty(),
                                observed.resourceEvidence()));
                    } else {
                        String failure = iteration.failure().map(Object::toString)
                                .orElse("incomplete measured iteration");
                        failures.add("measured iteration " + index + ": " + failure);
                        indexedEvidence.set(index, failedEvidence(index,
                                new IllegalStateException(failure), observed == null
                                        ? Optional.empty() : observed.cacheHit(), observed == null
                                        ? Optional.empty() : observed.sourceWork(), observed == null
                                        ? Optional.empty() : observed.resourceEvidence()));
                    }
                }
            }
        } catch (OutOfMemoryError failure) {
            failures.add("OUT_OF_MEMORY during PF-1.8 campaign: " + failure);
        } catch (RuntimeException failure) {
            failures.add("campaign failure: " + failure);
        }

        SaveSafetySnapshot after = null;
        Optional<SaveSafetyResult> safety = Optional.empty();
        Optional<String> safetyInspectionFailure = Optional.empty();
        try {
            after = snapshotProvider.capture(save);
            safety = Optional.of(safetyGate.compare(before, after));
        } catch (RuntimeException failure) {
            safetyInspectionFailure = Optional.of(failure.toString());
            failures.add("source safety inspection unavailable: " + failure);
        }
        if (authoritative == null) {
            authoritative = new Pf18IterationEvidence(Optional.empty(), Optional.empty(),
                    false, "UNAVAILABLE");
        }
        for (int index = 0; index < MEASURED_COUNT; index++) {
            if (indexedEvidence.get(index) == null) {
                failures.add("missing measured evidence for iteration " + index);
                indexedEvidence.set(index, failedEvidence(index,
                        new IllegalStateException("missing measured evidence"), Optional.empty(),
                        Optional.empty(), Optional.empty()));
            }
        }
        List<Pf18MeasuredIterationEvidence> measuredEvidence = List.copyOf(indexedEvidence);
        List<Long> samples = measuredEvidence.stream()
                .filter(Pf18MeasuredIterationEvidence::successful)
                .map(evidence -> evidence.parentWallClockNanoseconds().orElseThrow()).toList();
        List<Pf18ResourceEvidence> resources = measuredEvidence.stream()
                .filter(Pf18MeasuredIterationEvidence::successful)
                .map(evidence -> evidence.resourceEvidence().orElseThrow()).toList();
        Path reportPath = campaign.resolve("macro-report.txt");
        Pf18MacroReport report = new Pf18MacroReport(
                sha, before.mainSave().sha256().orElseThrow().sha256Hex(), workload.id(),
                workload.family().name(), save, workload.radius().blocks(), mode, preparation,
                environment, mode == ExecutionMode.PROCESS_COLD ? 0 : WARMUP_COUNT,
                MEASURED_COUNT, authoritative.semanticFingerprint(), authoritative.imageFingerprint(),
                workloadContract(workload), resources, measuredEvidence, samples,
                percentile(samples, 0), percentile(samples, 50), percentile(samples, 95),
                percentile(samples, 100), failures, safety, safetyInspectionFailure, before,
                Optional.ofNullable(after), mode != ExecutionMode.CACHE_WARM
                || measuredEvidence.stream().allMatch(evidence -> evidence.cacheHit().orElse(false)),
                cacheEvidence, reportPath);
        try {
            Files.writeString(reportPath, new Pf18MacroReportRenderer().render(report),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write PF-1.8 macro report", exception);
        }
        return report;
    }

    private static Pf18MeasuredIterationEvidence successfulEvidence(int index, long duration,
                                                                     Pf18IterationEvidence evidence,
                                                                     Pf18ResourceEvidence resources) {
        return new Pf18MeasuredIterationEvidence(index, duration < 0 ? OptionalLong.empty()
                : OptionalLong.of(duration), true, Optional.of(evidence.cacheHit()),
                Optional.of(evidence.sourceWork()), Optional.empty(), Optional.of(resources));
    }

    private static Pf18MeasuredIterationEvidence failedEvidence(int index, Exception failure,
                                                                Optional<Boolean> cacheHit,
                                                                Optional<String> sourceWork,
                                                                Optional<Pf18ResourceEvidence> resources) {
        return new Pf18MeasuredIterationEvidence(index, OptionalLong.empty(), false, cacheHit,
                sourceWork, Optional.of(failure.toString()), resources);
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

    private static OptionalLong percentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return OptionalLong.empty();
        List<Long> sorted = values.stream().sorted().toList();
        if (percentile == 0) return OptionalLong.of(sorted.getFirst());
        int index = Math.min(sorted.size() - 1,
                (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return OptionalLong.of(sorted.get(index));
    }

    private static String workloadContract(WorkloadSpec workload) {
        if (workload.family() == WorkloadFamily.MAP) {
            return "RenderActualOreMapUseCase; style=TOPOGRAPHIC; pixels-per-block=1; "
                    + "layers=TERRAIN,SURFACE; center=derived from save/player; ore overlays=none; "
                    + "radius=R" + workload.radius().blocks();
        }
        return "RenderRockMapUseCase; mode=UPPER_ROCK; center=derived from save/player; "
                + "radius=R" + workload.radius().blocks();
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
}
