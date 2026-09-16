package cartographer.perf.lab;

import cartographer.application.ProgressReporter;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.baseline.ReferenceBaseline;
import cartographer.perf.baseline.ReferenceBaselineFactory;
import cartographer.perf.benchmark.BenchmarkOperationResult;
import cartographer.perf.benchmark.BenchmarkPlan;
import cartographer.perf.benchmark.BenchmarkRunResult;
import cartographer.perf.benchmark.BenchmarkRunner;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.metrics.ExecutionMode;
import cartographer.perf.metrics.PerformanceEnvironment;
import cartographer.perf.report.BaselineReport;
import cartographer.perf.report.BaselineReportFactory;
import cartographer.perf.report.BaselineReportRenderer;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Runs one real-save MAP_R128 baseline workflow for local reviewer validation. */
public final class PerformanceLabRunner {
    private static final int RADIUS_BLOCKS = 128;
    private static final int WARMUP_COUNT = 2;
    private static final int MEASURED_ITERATION_COUNT = 5;

    public void run(PerformanceLabConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration is required");
        Path savePath = configuration.savePath();
        SaveSafetySnapshotter snapshotter = new SaveSafetySnapshotter();
        SaveSafetySnapshot before = snapshotter.capture(savePath);

        BenchmarkRunResult benchmarkResult = new BenchmarkRunner().run(
                new BenchmarkPlan(
                        new cartographer.perf.workload.MapWorkload(
                                cartographer.perf.workload.RadiusProfile.R128
                        ),
                        ExecutionMode.JVM_WARM,
                        WARMUP_COUNT,
                        MEASURED_ITERATION_COUNT
                ),
                mapOperation(savePath)
        );
        SaveSafetySnapshot after = snapshotter.capture(savePath);
        SaveSafetyResult safetyResult = new SaveSafetyGate().compare(before, after);

        ReferenceBaseline baseline = createBaseline(configuration, benchmarkResult, before, safetyResult);
        BaselineReport report = BaselineReportFactory.from(baseline, safetyResult);
        String rendered = new BaselineReportRenderer().render(report);
        writeReport(configuration.reportPath(), rendered);
        printSummary(configuration.reportPath(), report);
        if (safetyResult.status() != cartographer.perf.safety.SaveSafetyStatus.PASS) {
            throw new IllegalStateException("Save safety gate failed; see report: "
                    + configuration.reportPath());
        }
    }

    private static ReferenceBaseline createBaseline(
            PerformanceLabConfiguration configuration,
            BenchmarkRunResult benchmarkResult,
            SaveSafetySnapshot before,
            SaveSafetyResult safetyResult
    ) {
        try {
            return ReferenceBaselineFactory.from(
                    configuration.gitCommitSha(),
                    before.mainSave().sha256().orElseThrow().sha256Hex(),
                    environment(),
                    benchmarkResult
            );
        } catch (IllegalArgumentException invalidEvidence) {
            if (safetyResult.status() != cartographer.perf.safety.SaveSafetyStatus.PASS) {
                System.err.println("Save safety: FAIL");
                safetyResult.violations().forEach(violation ->
                        System.err.println(violation.type() + ": " + violation.path()));
            }
            throw new IllegalStateException(
                    "Benchmark output cannot become a reference baseline",
                    invalidEvidence
            );
        }
    }

    private static cartographer.perf.benchmark.BenchmarkOperation mapOperation(Path savePath) {
        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        MapRenderer renderer = new MapRenderer();
        RenderOptions renderOptions = new RenderOptions(
                RADIUS_BLOCKS,
                1,
                RenderStyle.SIMPLE,
                RenderLayer.defaults()
        );
        return workload -> {
            if (!(workload instanceof cartographer.perf.workload.MapWorkload mapWorkload)
                    || mapWorkload.radius().blocks() != RADIUS_BLOCKS) {
                throw new IllegalArgumentException("Performance lab requires MAP_R128");
            }
            WorldMetadata metadata = metadataReader.read(savePath, ProgressReporter.NONE);
            WorldPosition center = new WorldPosition(metadata.originX(), 0, metadata.originZ());
            List<MapChunk> chunks = reader.readMapChunksAround(
                    savePath,
                    center,
                    RADIUS_BLOCKS,
                    new ReadDiagnostics(),
                    ProgressReporter.NONE
            );
            var rendered = renderer.render(
                    center,
                    HomeState.absent(),
                    chunks,
                    renderOptions
            );
            return BenchmarkOperationResult.success(ImageFingerprinter.fingerprint(rendered.image()));
        };
    }

    private static PerformanceEnvironment environment() {
        return new PerformanceEnvironment(
                Runtime.version().toString(),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory()
        );
    }

    private static void writeReport(Path reportPath, String rendered) {
        try {
            Path parent = reportPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    reportPath,
                    rendered,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write performance report: " + reportPath, exception);
        }
    }

    private static void printSummary(Path reportPath, BaselineReport report) {
        System.out.println("Performance report generated:");
        System.out.println(reportPath);
        System.out.println("Workload: " + report.workloadId());
        System.out.println("Execution mode: " + report.executionMode().name());
        System.out.println("Measured iterations: " + report.measuredIterationCount());
        System.out.println("Save safety: " + report.saveSafetyStatus().name());
    }
}
