package cartographer.snapshot;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.ServerMapRegion;
import cartographer.perf.MapRegionSnapshotRead;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Healthy-complete PF-2.4 mapregion snapshot consumer. */
public final class SnapshotMapRegionReader {
    private final RenderDataCacheStore cacheStore;

    public SnapshotMapRegionReader(RenderDataCacheStore cacheStore) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public Optional<Result> read(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");
        try {
            Optional<WorldDataSnapshot> snapshot =
                    WorldDataSnapshot.openOrCreate(cacheStore, savePath);
            if (snapshot.isEmpty()) {
                return Optional.empty();
            }
            MapRegionSnapshotRead read = snapshot.orElseThrow()
                    .mapRegionStore()
                    .readAll();
            if (!read.healthyComplete()) {
                return Optional.empty();
            }
            List<EnvironmentProfile> environment = read.entries().stream()
                    .map(entry -> entry.environmentProfile())
                    .toList();
            List<GeologicProvinceSummary> geology = read.entries().stream()
                    .map(entry -> entry.geologySummary())
                    .flatMap(Optional::stream)
                    .toList();
            List<ServerMapRegion> resourceRegions = read.entries().stream()
                    .map(entry -> new ServerMapRegion(
                            entry.coordinate(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            entry.oreMaps(),
                            List.of()
                    ))
                    .toList();
            return Optional.of(new Result(
                    environment,
                    geology,
                    resourceRegions
            ));
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            return Optional.empty();
        }
    }

    public record Result(
            List<EnvironmentProfile> environmentProfiles,
            List<GeologicProvinceSummary> geologySummaries,
            List<ServerMapRegion> resourceRegions
    ) {
        public Result {
            environmentProfiles = List.copyOf(
                    Objects.requireNonNull(
                            environmentProfiles,
                            "environmentProfiles is required"
                    )
            );
            geologySummaries = List.copyOf(
                    Objects.requireNonNull(
                            geologySummaries,
                            "geologySummaries is required"
                    )
            );
            resourceRegions = List.copyOf(
                    Objects.requireNonNull(
                            resourceRegions,
                            "resourceRegions is required"
                    )
            );
        }
    }
}
