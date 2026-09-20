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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Pf28SnapshotValidationRunner {
    private static final List<Integer> RADII =
            List.of(1024, 2048, 4096);

    public Pf28SnapshotValidationReport run(
            Path savePath,
            String candidateSha,
            Path outputRoot
    ) {
        return runPrepared(
                Pf28ValidationPaths.prepare(savePath, outputRoot),
                candidateSha
        );
    }

    Pf28SnapshotValidationReport runPrepared(
            Pf28ValidationPaths paths,
            String candidateSha
    ) {
        Path save = Objects.requireNonNull(
                paths,
                "validation paths are required"
        ).save();
        String sha = Pf28SnapshotValidationReport.fullSha(candidateSha);
        Path root = paths.outputRoot();
        Path cacheRoot = paths.cacheRoot();

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

        Path stateRoot = paths.renderStateRoot();
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
            int sourceConnectionsOpened = Math.subtractExact(
                    probeAfter.connectionsOpened(),
                    probeBefore.connectionsOpened()
            );
            int sourceConnectionsClosed = Math.subtractExact(
                    probeAfter.connectionsClosed(),
                    probeBefore.connectionsClosed()
            );
            var cacheReport = warmResult.renderDataCacheReport();
            samples.add(new Pf28WarmRenderSample(
                    radius,
                    warmElapsed,
                    sourceElapsed,
                    warm.evidence(),
                    sourceConnectionsOpened,
                    sourceConnectionsClosed,
                    cacheReport.terrain().requested(),
                    cacheReport.terrain().hits(),
                    cacheReport.terrain().misses(),
                    cacheReport.surface().requested(),
                    cacheReport.surface().hits(),
                    warmResult.geometry().equals(source.geometry()),
                    ImageFingerprinter.fingerprint(warmResult.image()),
                    ImageFingerprinter.fingerprint(source.image())
            ));
        }

        boolean revisionInvalidationPassed =
                new Pf28RevisionInvalidationProbe().verify(
                        paths.revisionProbeRoot()
                );

        SaveSafetySnapshot after = safetySnapshotter.capture(save);
        SaveSafetyResult safety =
                new SaveSafetyGate().compare(before, after);

        return new Pf28SnapshotValidationReport(
                sha,
                prepared.revisionHash(),
                Pf28ColdSnapshotCoverage.from(prepared),
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

    private VcdbsReader createReader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private long elapsedSince(long start) {
        return Math.max(0L, System.nanoTime() - start);
    }
}
