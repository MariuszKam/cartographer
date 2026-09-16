package cartographer.perf.macro;

import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.geology.rock.RockMapMode;
import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.baseline.ReferenceBaselineFactory;
import cartographer.perf.benchmark.BenchmarkOperation;
import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.benchmark.BenchmarkRunner;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.workload.WorkloadSpec;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.RockMapRenderer;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Executes the first narrow, reviewer-controlled real-save macro baselines. */
public final class MacroBaselineRunner {
    private static final int WARMUP_COUNT = 2;
    private static final int MEASURED_ITERATION_COUNT = 5;
    private static final int HASH_BUFFER_SIZE = 16 * 1024;

    private final BenchmarkExecutor benchmarkExecutor;
    private final BenchmarkOperationFactory operationFactory;
    private final SaveFingerprintProvider saveFingerprintProvider;
    private final MacroBaselineRenderer renderer;

    public MacroBaselineRunner() {
        RenderRockMapUseCase rockUseCase = new RenderRockMapUseCase(
                new VcdbsReader(
                        new PlayerDataParser(),
                        new MapChunkParser(),
                        new ChunkParser(),
                        new RegistryParser()
                ),
                new WorldMetadataReader(),
                new RockMapRenderer()
        );
        this.benchmarkExecutor = new BenchmarkRunner()::run;
        this.operationFactory = (savePath, workload) -> rockOperation(
                rockUseCase, savePath, workload);
        this.saveFingerprintProvider = MacroBaselineRunner::sha256;
        this.renderer = new MacroBaselineRenderer();
    }

    MacroBaselineRunner(
            BenchmarkExecutor benchmarkExecutor,
            BenchmarkOperationFactory operationFactory,
            SaveFingerprintProvider saveFingerprintProvider,
            MacroBaselineRenderer renderer
    ) {
        this.benchmarkExecutor = Objects.requireNonNull(
                benchmarkExecutor, "benchmark executor is required");
        this.operationFactory = Objects.requireNonNull(
                operationFactory, "operation factory is required");
        this.saveFingerprintProvider = Objects.requireNonNull(
                saveFingerprintProvider, "save fingerprint provider is required");
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
    }

    public MacroBaselineResult run(
            Path savePath,
            String workloadId,
            String gitSha,
            PerformanceEnvironment environment,
            Path outputDirectory
    ) {
        Path save = normalizeSave(savePath);
        WorkloadSpec workload = MacroWorkloadResolver.resolve(workloadId);
        String normalizedSha = exactGitSha(gitSha);
        Objects.requireNonNull(environment, "environment is required");
        Path output = Objects.requireNonNull(outputDirectory,
                "output directory is required").toAbsolutePath().normalize();

        BenchmarkPlan plan = new BenchmarkPlan(
                workload, ExecutionMode.JVM_WARM,
                WARMUP_COUNT, MEASURED_ITERATION_COUNT
        );
        BenchmarkRunResult runResult = benchmarkExecutor.execute(
                plan, operationFactory.create(save, workload));
        if (runResult.status() != cartographer.perf.benchmark.BenchmarkExecutionStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Macro benchmark did not succeed: " + runResult.status());
        }

        String saveFingerprint;
        try {
            saveFingerprint = saveFingerprintProvider.fingerprint(save);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot fingerprint save after macro benchmark: " + save, exception);
        }
        ReferenceBaseline baseline = ReferenceBaselineFactory.from(
                normalizedSha, saveFingerprint, environment, runResult);
        Path reportPath = output.resolve(workload.id() + "-" + normalizedSha + ".txt");
        try {
            Files.createDirectories(output);
            Files.writeString(
                    reportPath,
                    renderer.render(baseline),
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write macro baseline report: "
                    + reportPath, exception);
        }
        return new MacroBaselineResult(baseline, reportPath);
    }

    private static BenchmarkOperation rockOperation(
            RenderRockMapUseCase rockUseCase,
            Path savePath,
            WorkloadSpec workload
    ) {
        return ignored -> {
            RenderRockMapResult result = rockUseCase.execute(new RenderRockMapRequest(
                    savePath,
                    RockMapMode.UPPER_ROCK,
                    workload.radius().blocks(),
                    Optional.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty()
            ));
            return BenchmarkOperationResult.success(
                    ImageFingerprinter.fingerprint(result.rendered().image())
            );
        };
    }

    private static Path normalizeSave(Path savePath) {
        Objects.requireNonNull(savePath, "save path is required");
        Path normalized = savePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(
                    "Save path must be an existing regular file: " + normalized);
        }
        return normalized;
    }

    private static String exactGitSha(String value) {
        Objects.requireNonNull(value, "gitSha is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitSha must be a full 40-character SHA");
        }
        return normalized;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            try (var input = Files.newInputStream(path)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    @FunctionalInterface
    interface BenchmarkExecutor {
        BenchmarkRunResult execute(BenchmarkPlan plan, BenchmarkOperation operation);
    }

    @FunctionalInterface
    interface BenchmarkOperationFactory {
        BenchmarkOperation create(Path savePath, WorkloadSpec workload);
    }

    @FunctionalInterface
    interface SaveFingerprintProvider {
        String fingerprint(Path savePath) throws IOException;
    }
}
