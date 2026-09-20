package cartographer.perf.snapshot;

import cartographer.geology.rock.RockMap;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldSnapshotHeader;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.fingerprint.ResultFingerprint;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.metrics.Pf18ResourceSampler;
import cartographer.perf.safety.SaveSafetyGate;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetySnapshot;
import cartographer.perf.safety.SaveSafetySnapshotter;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RockLegendEntry;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.snapshot.SnapshotUpperRockReader;
import cartographer.snapshot.SnapshotUpperRockRenderReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        SnapshotUpperRockRenderReader direct =
                new SnapshotUpperRockRenderReader(
                        cacheStore,
                        renderer
                );
        SnapshotUpperRockReader exact =
                new SnapshotUpperRockReader(cacheStore);
        Pf18ResourceSampler resourceSampler =
                new Pf18ResourceSampler();

        List<Pf3RockWarmRenderSample> rockSamples =
                new ArrayList<>();
        for (int radius : RADII) {
            MeasuredRock warm = measureDirect(
                    direct,
                    resourceSampler,
                    save,
                    header,
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

    private MeasuredRock measureDirect(
            SnapshotUpperRockRenderReader direct,
            Pf18ResourceSampler resourceSampler,
            Path save,
            WorldSnapshotHeader header,
            WorldPosition center,
            int radius
    ) {
        long started = System.nanoTime();
        Pf18ResourceSampler.Measured<RockMapRenderResult> measured =
                resourceSampler.measure(() -> direct.read(
                        save,
                        header.metadata(),
                        header.blockRegistry(),
                        center,
                        radius
                ).orElseThrow(() -> new IllegalStateException(
                        "PF-3 direct UPPER_ROCK snapshot render is unavailable at R"
                                + radius
                )));
        long elapsed = elapsedSince(started);
        return new MeasuredRock(
                elapsed,
                measured.evidence(),
                snapshot(measured.result())
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

    private long elapsedSince(long start) {
        return Math.max(0L, System.nanoTime() - start);
    }

    private record MeasuredRock(
            long elapsedNanoseconds,
            Pf18ResourceEvidence resources,
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
