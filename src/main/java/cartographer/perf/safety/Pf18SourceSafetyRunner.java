package cartographer.perf.safety;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.RenderDataCacheRevision;
import cartographer.perf.TerrainTileStore;
import cartographer.perf.SurfaceTileStore;
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
            Pf18SourceSafetyReport.Pf18CacheEvidence evidence = cacheEvidence(save, cache);
            return report(save, cache, safety, completed[0], evidence, Optional.empty());
        } catch (RealSaveValidationException failure) {
            Pf18SourceSafetyReport.Pf18CacheEvidence evidence = cacheEvidence(save, cache);
            Optional<SaveSafetyResult> safety = failure.safetyResult();
            return report(save, cache, safety.orElse(null), completed[0], evidence,
                    Optional.ofNullable(failure.getCause() == null
                            ? failure.getMessage() : failure.getCause().toString()));
        } catch (RuntimeException failure) {
            Pf18SourceSafetyReport.Pf18CacheEvidence evidence = cacheEvidence(save, cache);
            return report(save, cache, null, completed[0], evidence,
                    Optional.of(failure.toString()));
        }
    }

    private Pf18SourceSafetyReport report(
            Path save,
            Path cache,
            SaveSafetyResult safety,
            boolean completed,
            Pf18SourceSafetyReport.Pf18CacheEvidence evidence,
            Optional<String> failure
    ) {
        Pf18SourceSafetyStatus status;
        if (!completed || failure.isPresent()) {
            status = safety == null ? Pf18SourceSafetyStatus.INCONCLUSIVE
                    : Pf18SourceSafetyStatus.FAIL;
        } else if (safety == null) {
            status = Pf18SourceSafetyStatus.INCONCLUSIVE;
        } else if (safety.status() == SaveSafetyStatus.FAIL) {
            status = Pf18SourceSafetyStatus.FAIL;
        } else if (!evidence.qualifyingManifest() || !evidence.contained()) {
            status = Pf18SourceSafetyStatus.INCONCLUSIVE;
        } else {
            status = Pf18SourceSafetyStatus.PASS;
        }
        return new Pf18SourceSafetyReport(
                save, cache, WORKLOAD, status, Optional.ofNullable(safety), completed,
                evidence, failure
        );
    }

    private static Pf18SourceSafetyReport.Pf18CacheEvidence cacheEvidence(
            Path save,
            Path cacheRoot
    ) {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = store.observe(save);
        Path manifest = store.manifestPath(revision);
        Path terrain = new TerrainTileStore(store, revision).databasePath();
        Path surface = new SurfaceTileStore(store, revision).databasePath();
        List<Path> artifacts = new java.util.ArrayList<>();
        boolean contained = true;
        boolean manifestPresent = Files.isRegularFile(manifest);
        boolean terrainPresent = Files.isRegularFile(terrain);
        boolean surfacePresent = Files.isRegularFile(surface);
        for (Path artifact : List.of(manifest, terrain, surface)) {
            if (!Files.isRegularFile(artifact)) {
                continue;
            }
            Path normalized = artifact.toAbsolutePath().normalize();
            Path resolvedArtifact;
            Path resolvedCacheRoot;
            Path resolvedSave;
            Path resolvedSaveDirectory;
            try {
                resolvedArtifact = artifact.toRealPath().normalize();
                resolvedCacheRoot = cacheRoot.toRealPath().normalize();
                resolvedSave = save.toRealPath().normalize();
                resolvedSaveDirectory = save.getParent().toRealPath().normalize();
            } catch (IOException exception) {
                contained = false;
                continue;
            }
            if (!normalized.startsWith(cacheRoot)
                    || !resolvedArtifact.startsWith(resolvedCacheRoot)
                    || normalized.equals(save)
                    || resolvedArtifact.equals(resolvedSave)
                    || normalized.startsWith(save.getParent())
                    || resolvedArtifact.startsWith(resolvedSaveDirectory)) {
                contained = false;
            }
            artifacts.add(normalized);
        }
        boolean qualifyingManifest = manifestPresent
                && contained
                && store.find(revision).isPresent();
        return new Pf18SourceSafetyReport.Pf18CacheEvidence(
                manifestPresent, qualifyingManifest, terrainPresent, surfacePresent,
                contained, artifacts);
    }

    private static void runProductionWorkload(Path savePath, Path cacheRoot) {
        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(), new MapChunkParser(), new ChunkParser(),
                new RegistryParser());
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        RenderActualOreMapUseCase useCase = new RenderActualOreMapUseCase(
                reader,
                new HomeStore(cacheRoot.resolve("pf18-state").resolve("home.properties")),
                new MarkerStore(cacheRoot.resolve("pf18-state")),
                new MapRenderer(),
                new UserMarkerRenderer(),
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
        if (Files.exists(cache)) {
            Path resolvedCache;
            Path resolvedSave;
            Path resolvedSaveParent;
            try {
                resolvedCache = cache.toRealPath().normalize();
                resolvedSave = save.toRealPath().normalize();
                resolvedSaveParent = saveParent.toRealPath().normalize();
            } catch (IOException exception) {
                throw new IllegalArgumentException(
                        "cannot resolve cache root and source paths for containment: " + cache,
                        exception);
            }
            if (resolvedCache.equals(resolvedSave)
                    || resolvedCache.startsWith(resolvedSaveParent)
                    || resolvedSaveParent.startsWith(resolvedCache)) {
                throw new IllegalArgumentException(
                        "cache root must be outside the source save directory: " + cache);
            }
        }
        return cache;
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
