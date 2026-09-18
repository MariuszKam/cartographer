package cartographer.perf.safety;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.perf.RenderDataCacheStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.scanner.ActualBlockYFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Composes the existing save safety snapshots with a PF-1.8 render workload. */
public final class Pf18SourceSafetyRunner {
    public static final String WORKLOAD = "PF18_SOURCE_SAFETY_R128";

    private final SaveSafetySnapshotter snapshotter;
    private final SaveSafetyGate gate;
    private final SafetyOperation operation;

    public Pf18SourceSafetyRunner() {
        this(new SaveSafetySnapshotter(), new SaveSafetyGate(),
                Pf18SourceSafetyRunner::runProductionWorkload);
    }

    Pf18SourceSafetyRunner(
            SaveSafetySnapshotter snapshotter,
            SaveSafetyGate gate,
            SafetyOperation operation
    ) {
        this.snapshotter = Objects.requireNonNull(snapshotter, "snapshotter is required");
        this.gate = Objects.requireNonNull(gate, "gate is required");
        this.operation = Objects.requireNonNull(operation, "operation is required");
    }

    public Pf18SourceSafetyReport validate(Path savePath, Path cacheRoot) {
        Path save = normalize(savePath, "save path");
        Path cache = validateCacheRoot(save, cacheRoot);
        final boolean[] completed = {false};
        RealSaveValidationRunner delegate = new RealSaveValidationRunner(
                snapshotter,
                gate,
                path -> {
                    operation.execute(path, cache);
                    completed[0] = true;
                }
        );
        try {
            SaveSafetyResult safety = delegate.validate(save);
            List<Path> artifacts = cacheArtifacts(cache);
            return report(save, cache, safety,
                    completed[0], artifacts, Optional.empty());
        } catch (RealSaveValidationException failure) {
            List<Path> artifacts = cacheArtifacts(cache);
            Optional<SaveSafetyResult> safety = failure.safetyResult();
            return report(save, cache, safety.orElse(null), completed[0], artifacts,
                    Optional.ofNullable(failure.getCause() == null
                            ? failure.getMessage() : failure.getCause().toString()));
        } catch (RuntimeException failure) {
            List<Path> artifacts = cacheArtifacts(cache);
            return report(save, cache, null, completed[0], artifacts,
                    Optional.of(failure.toString()));
        }
    }

    private Pf18SourceSafetyReport report(
            Path save,
            Path cache,
            SaveSafetyResult safety,
            boolean completed,
            List<Path> artifacts,
            Optional<String> failure
    ) {
        Pf18SourceSafetyStatus status;
        if (!completed || failure.isPresent()) {
            status = safety == null ? Pf18SourceSafetyStatus.INCONCLUSIVE
                    : Pf18SourceSafetyStatus.FAIL;
        } else if (safety == null) {
            status = Pf18SourceSafetyStatus.INCONCLUSIVE;
        } else {
            status = safety.status() == SaveSafetyStatus.PASS
                    ? Pf18SourceSafetyStatus.PASS : Pf18SourceSafetyStatus.FAIL;
        }
        return new Pf18SourceSafetyReport(
                save, cache, WORKLOAD, status, Optional.ofNullable(safety), completed,
                !artifacts.isEmpty(), artifacts, failure
        );
    }

    private static void runProductionWorkload(Path savePath, Path cacheRoot) {
        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(), new MapChunkParser(), new ChunkParser(),
                new RegistryParser(), new SqliteSaveConnection());
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        RenderActualOreMapUseCase useCase = new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                new HomeStore(cacheRoot.resolve("pf18-state").resolve("home.properties")),
                new MarkerStore(cacheRoot.resolve("pf18-state").resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new cartographer.scanner.ActualBlockMapScanner(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new cartographer.application.OreChunkPositionPlanner(),
                new cartographer.save.SaveSessionFactory(
                        new SqliteSaveConnection(), reader, metadataReader),
                new RenderDataCacheStore(cacheRoot)
        );
        useCase.execute(new RenderActualOreMapRequest(
                savePath,
                128,
                1,
                RenderStyle.TOPOGRAPHIC,
                java.util.Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.empty()
        ));
    }

    private static Path validateCacheRoot(Path save, Path cacheRoot) {
        Path cache = normalize(cacheRoot, "cache root");
        Path saveParent = Objects.requireNonNull(save.getParent(), "save parent is required")
                .toAbsolutePath().normalize();
        if (cache.equals(save) || cache.startsWith(saveParent) || saveParent.startsWith(cache)) {
            throw new IllegalArgumentException(
                    "cache root must be outside the source save directory: " + cache);
        }
        if (Files.exists(cache) && !Files.isDirectory(cache)) {
            throw new IllegalArgumentException("cache root must be a directory: " + cache);
        }
        return cache;
    }

    private static List<Path> cacheArtifacts(Path cacheRoot) {
        if (!Files.isDirectory(cacheRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(cacheRoot)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect PF-1.8 cache root: " + cacheRoot, exception);
        }
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name + " is required")
                .toAbsolutePath().normalize();
    }

    @FunctionalInterface
    interface SafetyOperation {
        void execute(Path savePath, Path cacheRoot) throws Exception;
    }
}
