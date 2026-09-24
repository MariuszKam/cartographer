package cartographer.perf.snapshot;

import cartographer.application.ProgressReporter;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldSnapshotHeader;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.metrics.Pf18ResourceSampler;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RockLegendEntry;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.snapshot.SnapshotUpperRockReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class Pf3RenderSizedValidationRunner {
    private static final List<Integer> RADII =
            List.of(1024, 2048, 4096);

    public Pf3RenderSizedValidationReport run(
            Path savePath,
            String candidateSha,
            Path outputRoot
    ) {
        Pf28ValidationPaths paths =
                Pf28ValidationPaths.prepare(savePath, outputRoot);
        Path save = paths.save();
        String sha = Pf28SnapshotValidationReport.fullSha(candidateSha);

        SaveSafetySnapshotter snapshotter =
                new SaveSafetySnapshotter();
        SaveSafetySnapshot before = snapshotter.capture(save);

        Pf28SnapshotValidationReport mapSurface =
                new Pf28SnapshotValidationRunner().runPrepared(
                        paths,
                        sha
                );

        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(paths.cacheRoot());
        WorldSnapshotHeader header =
                new SnapshotWorldHeaderReader(cacheStore)
                        .read(save)
                        .orElseThrow(() -> new IllegalStateException(
                                "PF-3 validation requires a complete snapshot header"
                        ));
        WorldPosition center = header.player()
                .orElseThrow(() -> new IllegalStateException(
                        "PF-3 ROCK validation requires a player position"
                ));

        RockMapRenderer renderer = new RockMapRenderer();
        SnapshotUpperRockReader exact =
                new SnapshotUpperRockReader(cacheStore);
        VcdbsReader reader = createReader();
        WorldMetadataReader metadataReader =
                new WorldMetadataReader();
        RecordingSqliteSaveConnection warmConnections =
                new RecordingSqliteSaveConnection();
        SaveSessionFactory warmSessionFactory =
                new SaveSessionFactory(
                        warmConnections,
                        reader,
                        metadataReader
                );
        RenderRockMapUseCase warmRockUseCase =
                new RenderRockMapUseCase(
                        reader,
                        renderer,
                        warmSessionFactory,
                        cacheStore
                );
        Pf18ResourceSampler resourceSampler =
                new Pf18ResourceSampler();

        List<Pf3RockWarmRenderSample> rockSamples =
                new ArrayList<>();
        for (int radius : RADII) {
            MeasuredRock warm = measureWarmRock(
                    warmRockUseCase,
                    warmConnections,
                    resourceSampler,
                    save,
                    center,
                    radius
            );
            TimedRock exactResult = renderExact(
                    exact,
                    renderer,
                    save,
                    header,
                    center,
                    radius
            );
            RockSnapshot warmSnapshot = warm.snapshot();
            RockSnapshot exactSnapshot = exactResult.snapshot();

            rockSamples.add(new Pf3RockWarmRenderSample(
                    radius,
                    warm.elapsedNanoseconds(),
                    exactResult.elapsedNanoseconds(),
                    warm.resources(),
                    warm.sourceConnectionsOpened(),
                    warm.sourceConnectionsClosed(),
                    warm.retainedMapAbsent(),
                    warmSnapshot.geometry().equals(
                            exactSnapshot.geometry()
                    ),
                    warmSnapshot.legend().equals(
                            exactSnapshot.legend()
                    ),
                    warmSnapshot.observedCount()
                            == exactSnapshot.observedCount()
                            && warmSnapshot.noRockCount()
                            == exactSnapshot.noRockCount()
                            && warmSnapshot.unavailableCount()
                            == exactSnapshot.unavailableCount(),
                    warmSnapshot.fingerprint(),
                    exactSnapshot.fingerprint()
            ));
        }

        SaveSafetySnapshot after = snapshotter.capture(save);
        SaveSafetyResult fullSafety =
                new SaveSafetyGate().compare(before, after);

        return new Pf3RenderSizedValidationReport(
                mapSurface,
                fullSafety,
                rockSamples
        );
    }

    private MeasuredRock measureWarmRock(
            RenderRockMapUseCase useCase,
            RecordingSqliteSaveConnection connections,
            Pf18ResourceSampler resourceSampler,
            Path save,
            WorldPosition center,
            int radius
    ) {
        RenderRockMapRequest request = new RenderRockMapRequest(
                save,
                RockMapMode.UPPER_ROCK,
                radius,
                Optional.of(center),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty()
        );
        RecordingSqliteSaveConnection.Snapshot probeBefore =
                connections.snapshot();

        long started = System.nanoTime();
        Pf18ResourceSampler.Measured<RenderRockMapResult> measured =
                resourceSampler.measure(() -> useCase.executeRenderOnly(
                        request,
                        ProgressReporter.NONE
                ));
        long elapsed = elapsedSince(started);
        RecordingSqliteSaveConnection.Snapshot probeAfter =
                connections.snapshot();

        RenderRockMapResult result = measured.result();
        return new MeasuredRock(
                elapsed,
                measured.evidence(),
                Math.subtractExact(
                        probeAfter.connectionsOpened(),
                        probeBefore.connectionsOpened()
                ),
                Math.subtractExact(
                        probeAfter.connectionsClosed(),
                        probeBefore.connectionsClosed()
                ),
                result.retainedMap().isEmpty(),
                snapshot(result.rendered())
        );
    }

    private TimedRock renderExact(
            SnapshotUpperRockReader exact,
            RockMapRenderer renderer,
            Path save,
            WorldSnapshotHeader header,
            WorldPosition center,
            int radius
    ) {
        long started = System.nanoTime();
        RockMap map = exact.read(
                save,
                header.metadata(),
                header.blockRegistry(),
                center,
                radius
        ).orElseThrow(() -> new IllegalStateException(
                "PF-3 exact UPPER_ROCK snapshot oracle is unavailable at R"
                        + radius
        ));
        RockSnapshot snapshot = snapshot(renderer.render(map));
        return new TimedRock(
                elapsedSince(started),
                snapshot
        );
    }

    private RockSnapshot snapshot(RockMapRenderResult result) {
        Objects.requireNonNull(result, "ROCK render result is required");
        return new RockSnapshot(
                result.geometry(),
                result.legend(),
                result.observedCount(),
                result.noRockCount(),
                result.unavailableCount(),
                ImageFingerprinter.fingerprint(result.image())
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

    private record MeasuredRock(
            long elapsedNanoseconds,
            Pf18ResourceEvidence resources,
            int sourceConnectionsOpened,
            int sourceConnectionsClosed,
            boolean retainedMapAbsent,
            RockSnapshot snapshot
    ) {
    }

    private record TimedRock(
            long elapsedNanoseconds,
            RockSnapshot snapshot
    ) {
    }

    private record RockSnapshot(
            MapViewportGeometry geometry,
            List<RockLegendEntry> legend,
            long observedCount,
            long noRockCount,
            long unavailableCount,
            ResultFingerprint fingerprint
    ) {
        private RockSnapshot {
            Objects.requireNonNull(geometry, "geometry is required");
            legend = List.copyOf(Objects.requireNonNull(
                    legend,
                    "legend is required"
            ));
            Objects.requireNonNull(
                    fingerprint,
                    "fingerprint is required"
            );
        }
    }
}
