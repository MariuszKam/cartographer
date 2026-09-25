package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.environment.EnvironmentInterpreter;
import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.ServerMapRegion;
import cartographer.progress.ProgressReporter;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.SnapshotMapRegionReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves map-region overlay data from retained, snapshot, or source state. */
final class MapRegionOverlayResolver {
    private final VcdbsReader reader;
    private final EnvironmentInterpreter environmentInterpreter =
            new EnvironmentInterpreter();
    private final GeologicProvinceInterpreter geologicProvinceInterpreter =
            new GeologicProvinceInterpreter();
    private final Optional<SnapshotMapRegionReader> snapshotReader;

    MapRegionOverlayResolver(
            VcdbsReader reader,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.snapshotReader = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        ).map(SnapshotMapRegionReader::new);
    }

    Optional<MapRegionOverlayState> snapshot(
            Path savePath,
            RenderOptions options,
            ProgressReporter progress
    ) {
        boolean environmentRequested =
                options.layers().contains(RenderLayer.ENVIRONMENT);
        boolean geologyRequested =
                options.layers().contains(RenderLayer.GEOLOGY);
        if (!environmentRequested && !geologyRequested) {
            return Optional.of(new MapRegionOverlayState(
                    Optional.empty(),
                    Optional.empty()
            ));
        }
        if (snapshotReader.isEmpty()) {
            return Optional.empty();
        }

        progress.start("Reading map-region overlays from world snapshot");
        Optional<SnapshotMapRegionReader.Result> snapshot =
                snapshotReader.orElseThrow().read(savePath);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        SnapshotMapRegionReader.Result result = snapshot.orElseThrow();
        return Optional.of(new MapRegionOverlayState(
                environmentRequested
                        ? Optional.of(result.environmentProfiles())
                        : Optional.empty(),
                geologyRequested
                        ? Optional.of(result.geologySummaries())
                        : Optional.empty()
        ));
    }

    MapRegionOverlayState resolve(
            SaveSession saveSession,
            RenderOptions options,
            Optional<MapRegionOverlayState> retainedState,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        boolean environmentRequested =
                options.layers().contains(RenderLayer.ENVIRONMENT);
        boolean geologyRequested =
                options.layers().contains(RenderLayer.GEOLOGY);
        Optional<List<EnvironmentProfile>> retainedEnvironment =
                retainedState.flatMap(MapRegionOverlayState::environmentProfiles);
        Optional<List<GeologicProvinceSummary>> retainedGeology =
                retainedState.flatMap(MapRegionOverlayState::geologySummaries);
        boolean needEnvironment =
                environmentRequested && retainedEnvironment.isEmpty();
        boolean needGeology =
                geologyRequested && retainedGeology.isEmpty();

        Optional<SnapshotMapRegionReader.Result> snapshot =
                Optional.empty();
        if ((needEnvironment || needGeology)
                && snapshotReader.isPresent()) {
            progress.start("Reading map-region overlays from world snapshot");
            snapshot = snapshotReader.orElseThrow().read(
                    saveSession.savePath()
            );
        }
        Optional<List<EnvironmentProfile>> snapshotEnvironment =
                snapshot.map(
                        SnapshotMapRegionReader.Result::environmentProfiles
                );
        Optional<List<GeologicProvinceSummary>> snapshotGeology =
                snapshot.map(
                        SnapshotMapRegionReader.Result::geologySummaries
                );

        boolean sourceEnvironment =
                needEnvironment && snapshotEnvironment.isEmpty();
        boolean sourceGeology =
                needGeology && snapshotGeology.isEmpty();
        List<ServerMapRegion> regions =
                sourceEnvironment || sourceGeology
                        ? readMapRegionsWithProgress(
                        saveSession,
                        diagnostics,
                        progress
                )
                        : List.of();

        Optional<List<EnvironmentProfile>> environmentProfiles =
                retainedEnvironment.isPresent()
                        ? retainedEnvironment
                        : !environmentRequested
                        ? Optional.empty()
                        : snapshotEnvironment.isPresent()
                        ? snapshotEnvironment
                        : Optional.of(
                        regions.stream()
                                .map(environmentInterpreter::interpret)
                                .toList()
                );
        Optional<List<GeologicProvinceSummary>> geologySummaries =
                retainedGeology.isPresent()
                        ? retainedGeology
                        : !geologyRequested
                        ? Optional.empty()
                        : snapshotGeology.isPresent()
                        ? snapshotGeology
                        : Optional.of(
                        regions.stream()
                                .map(geologicProvinceInterpreter::summarize)
                                .flatMap(Optional::stream)
                                .toList()
                );

        return new MapRegionOverlayState(
                environmentProfiles,
                geologySummaries
        );
    }

    private List<ServerMapRegion> readMapRegionsWithProgress(
            SaveSession saveSession,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        progress.start("Reading map regions");
        return reader.readMapRegions(saveSession, diagnostics, progress);
    }
}
