package cartographer.perf.snapshot;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.application.PrepareWorldSnapshotRequest;
import cartographer.application.PrepareWorldSnapshotResult;
import cartographer.application.PrepareWorldSnapshotUseCase;
import cartographer.application.ProgressReporter;
import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.RenderDataCacheRevision;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.metrics.Pf18ResourceSampler;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SaveSessionLifecycleProbe;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.MultiActualBlockMapScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class Pf28SnapshotValidationRunner {
    private static final List<Integer> RADII =
            List.of(1024, 2048, 4096);

    public Pf28SnapshotValidationReport run(
            Path savePath,
            String candidateSha,
            Path outputRoot
    ) {
        Path save = normalizeSave(savePath);
        String sha = Pf28SnapshotValidationReport.fullSha(candidateSha);
        Path root = normalizeOutputRoot(save, outputRoot);
        Path cacheRoot = root.resolve("snapshot-cache");
        requireFreshCacheRoot(cacheRoot);

        SaveSafetySnapshotter safetySnapshotter =
                new SaveSafetySnapshotter();
        SaveSafetySnapshot before = safetySnapshotter.capture(save);

        VcdbsReader reader = createReader();
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(cacheRoot);
        PrepareWorldSnapshotUseCase prepare =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        metadataReader,
                        cacheStore
                );

        Pf18ResourceSampler resourceSampler = new Pf18ResourceSampler();
        long coldStart = System.nanoTime();
        Pf18ResourceSampler.Measured<PrepareWorldSnapshotResult> cold =
                resourceSampler.measure(
                        () -> prepare.execute(
                                new PrepareWorldSnapshotRequest(save),
                                ProgressReporter.NONE
                        )
                );
        long coldElapsed = elapsedSince(coldStart);
        PrepareWorldSnapshotResult prepared = cold.result();

        Path stateRoot = root.resolve("render-state");
        HomeStore homeStore =
                new HomeStore(stateRoot.resolve("home.properties"));
        MarkerStore markerStore =
                new MarkerStore(stateRoot.resolve("markers.csv"));

        SaveSessionLifecycleProbe warmProbe =
                SaveSessionLifecycleProbe.recording();
        SaveSessionFactory warmSessionFactory =
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader,
                        warmProbe
                );

        RenderActualOreMapUseCase warmUseCase =
                new RenderActualOreMapUseCase(
                        reader,
                        metadataReader,
                        homeStore,
                        markerStore,
                        new MapRenderer(),
                        new UserMarkerRenderer(),
                        new ActualBlockMapScanner(),
                        new ActualOreOverlayPainter(),
                        new MultiActualBlockMapScanner(),
                        new OreChunkPositionPlanner(),
                        warmSessionFactory,
                        cacheStore
                );

        RenderActualOreMapUseCase sourceUseCase =
                new RenderActualOreMapUseCase(
                        reader,
                        metadataReader,
                        homeStore,
                        markerStore,
                        new MapRenderer(),
                        new UserMarkerRenderer(),
                        new ActualBlockMapScanner(),
                        new ActualOreOverlayPainter()
                );

        List<Pf28WarmRenderSample> samples = new ArrayList<>();
        for (int radius : RADII) {
            RenderActualOreMapRequest request = request(save, radius);
            SaveSessionLifecycleProbe.Snapshot probeBefore =
                    warmProbe.snapshot();

            long warmStart = System.nanoTime();
            Pf18ResourceSampler.Measured<RenderActualOreMapResult> warm =
                    resourceSampler.measure(
                            () -> warmUseCase.execute(
                                    request,
                                    ProgressReporter.NONE
                            )
                    );
            long warmElapsed = elapsedSince(warmStart);
            SaveSessionLifecycleProbe.Snapshot probeAfter =
                    warmProbe.snapshot();

            long sourceStart = System.nanoTime();
            RenderActualOreMapResult source =
                    sourceUseCase.execute(
                            request,
                            ProgressReporter.NONE
                    );
            long sourceElapsed = elapsedSince(sourceStart);

            RenderActualOreMapResult warmResult = warm.result();
            boolean snapshotBacked =
                    warmResult.renderDataCacheReport().notes().stream()
                            .anyMatch(note -> note.contains(
                                    "PF-2.6 snapshot-backed warm path"
                            ));

            samples.add(new Pf28WarmRenderSample(
                    radius,
                    warmElapsed,
                    sourceElapsed,
                    warm.evidence(),
                    Math.subtractExact(
                            probeAfter.connectionsOpened(),
                            probeBefore.connectionsOpened()
                    ),
                    Math.subtractExact(
                            probeAfter.connectionsClosed(),
                            probeBefore.connectionsClosed()
                    ),
                    snapshotBacked,
                    warmResult.geometry().equals(source.geometry()),
                    ImageFingerprinter.fingerprint(warmResult.image()),
                    ImageFingerprinter.fingerprint(source.image())
            ));
        }

        boolean revisionInvalidationPassed =
                verifyRevisionInvalidation(root.resolve("revision-probe"));

        SaveSafetySnapshot after = safetySnapshotter.capture(save);
        SaveSafetyResult safety =
                new SaveSafetyGate().compare(before, after);

        return new Pf28SnapshotValidationReport(
                sha,
                prepared.revisionHash(),
                prepared.complete(),
                coldElapsed,
                cold.evidence(),
                safety,
                revisionInvalidationPassed,
                samples
        );
    }

    private RenderActualOreMapRequest request(
            Path save,
            int radius
    ) {
        return new RenderActualOreMapRequest(
                save,
                radius,
                1,
                RenderStyle.SIMPLE,
                EnumSet.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.SURFACE
                ),
                Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.empty()
        );
    }

    private boolean verifyRevisionInvalidation(Path root) {
        try {
            Files.createDirectories(root);
            Path source = root.resolve("revision-source.bin");
            Path cache = root.resolve("cache");
            Files.writeString(
                    source,
                    "revision-a",
                    StandardCharsets.UTF_8
            );

            RenderDataCacheStore store =
                    new RenderDataCacheStore(cache);
            RenderDataCacheRevision first = store.observe(source);
            store.publish(first);
            if (store.find(first).isEmpty()) {
                return false;
            }

            Files.writeString(
                    source,
                    "revision-b-with-different-size",
                    StandardCharsets.UTF_8
            );
            RenderDataCacheRevision second = store.observe(source);

            return !first.revisionHash().equals(second.revisionHash())
                    && store.find(second).isEmpty()
                    && store.find(first).isPresent();
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private VcdbsReader createReader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private Path normalizeSave(Path savePath) {
        Path save = java.util.Objects.requireNonNull(
                savePath,
                "savePath is required"
        ).toAbsolutePath().normalize();
        if (!Files.isRegularFile(save)) {
            throw new IllegalArgumentException(
                    "save must be an existing regular file: " + save
            );
        }
        return save;
    }

    private Path normalizeOutputRoot(
            Path save,
            Path outputRoot
    ) {
        Path root = java.util.Objects.requireNonNull(
                outputRoot,
                "outputRoot is required"
        ).toAbsolutePath().normalize();
        Path sourceDirectory = java.util.Objects.requireNonNull(
                save.getParent(),
                "save parent is required"
        );
        if (root.equals(save) || root.startsWith(sourceDirectory)) {
            throw new IllegalArgumentException(
                    "PF-2.8 outputRoot must be outside the source save directory"
            );
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot create PF-2.8 output root: " + root,
                    exception
            );
        }
        return root;
    }

    private void requireFreshCacheRoot(Path cacheRoot) {
        if (Files.exists(cacheRoot)) {
            throw new IllegalArgumentException(
                    "PF-2.8 requires a fresh cold cache root; already exists: "
                            + cacheRoot
            );
        }
    }

    private long elapsedSince(long start) {
        return Math.max(0L, System.nanoTime() - start);
    }
}
