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

        WorldDataSnapshot existing = snapshot.orElseThrow();
        Optional<WorldSnapshotPreparationSummary> summary =
                existing.preparationSummaryStore().read();

        boolean pf2ArtifactsPresent = summary.isPresent()
                || existing.headerStore().read().isPresent()
                || existing.indexCatalogStore().mapChunkScanComplete()
                || existing.mapRegionStore().scanComplete()
                || existing.resourceIndexStore().scanComplete();

        WorldSnapshotStatus.State state;
        if (!pf2ArtifactsPresent) {
            state = WorldSnapshotStatus.State.NOT_PREPARED;
        } else if (summary
                .filter(WorldSnapshotPreparationSummary::complete)
                .isPresent()) {
            state = WorldSnapshotStatus.State.READY;
        } else {
            state = WorldSnapshotStatus.State.PARTIAL;
        }

        return new WorldSnapshotStatus(
                normalized,
                revision.revisionHash(),
                state,
                summary
        );
    }
}
