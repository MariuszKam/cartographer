package cartographer.application;

import cartographer.perf.RenderDataCacheRevision;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldSnapshotPreparationSummary;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads PF-2 derived snapshot status without opening the source SQLite database.
 */
public final class InspectWorldSnapshotStatusUseCase {
    private final RenderDataCacheStore cacheStore;

    public InspectWorldSnapshotStatusUseCase(
            RenderDataCacheStore cacheStore
    ) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public WorldSnapshotStatus execute(Path savePath) {
        Path normalized = Objects.requireNonNull(
                savePath,
                "savePath is required"
        ).toAbsolutePath().normalize();
        RenderDataCacheRevision revision = cacheStore.observe(normalized);

        Optional<WorldDataSnapshot> snapshot =
                WorldDataSnapshot.openExisting(cacheStore, normalized);
        if (snapshot.isEmpty()) {
            return new WorldSnapshotStatus(
                    normalized,
                    revision.revisionHash(),
                    WorldSnapshotStatus.State.NOT_PREPARED,
                    Optional.empty()
            );
        }

        Optional<WorldSnapshotPreparationSummary> summary =
                snapshot.orElseThrow()
                        .preparationSummaryStore()
                        .read();
        WorldSnapshotStatus.State state = summary
                .filter(WorldSnapshotPreparationSummary::complete)
                .map(ignored -> WorldSnapshotStatus.State.READY)
                .orElse(WorldSnapshotStatus.State.PARTIAL);

        return new WorldSnapshotStatus(
                normalized,
                revision.revisionHash(),
                state,
                summary
        );
    }
}
