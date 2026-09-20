package cartographer.perf;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Sparse revision-scoped derived world snapshot backed by the existing
 * render-data cache stores.
 *
 * <p>This is not a decoded-world-in-memory cache and does not imply complete
 * world coverage. The Vintage Story save remains authoritative. Missing or
 * incompatible derived artifacts are cache misses and may be rebuilt from the
 * source save by the caller.</p>
 */
public final class WorldDataSnapshot {
    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final WorldSnapshotHeaderStore headerStore;
    private final TerrainTileStore terrainStore;
    private final SurfaceTileStore surfaceStore;
    private final WorldIndexCatalogStore indexCatalogStore;
    private final MapRegionSnapshotStore mapRegionStore;
    private final UpperRockTileStore upperRockTileStore;
    private final ResourceIndexStore resourceIndexStore;
    private final WorldPreparationStateStore preparationStateStore;
    private final WorldSnapshotPreparationSummaryStore preparationSummaryStore;

    private WorldDataSnapshot(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore is required");
        this.revision = Objects.requireNonNull(revision, "revision is required");
        this.headerStore = new WorldSnapshotHeaderStore(cacheStore, revision);
        this.terrainStore = new TerrainTileStore(cacheStore, revision);
        this.surfaceStore = new SurfaceTileStore(cacheStore, revision);
        this.indexCatalogStore = new WorldIndexCatalogStore(cacheStore, revision);
        this.mapRegionStore = new MapRegionSnapshotStore(cacheStore, revision);
        this.upperRockTileStore = new UpperRockTileStore(cacheStore, revision);
        this.resourceIndexStore = new ResourceIndexStore(cacheStore, revision);
        this.preparationStateStore =
                new WorldPreparationStateStore(cacheStore, revision);
        this.preparationSummaryStore =
                new WorldSnapshotPreparationSummaryStore(cacheStore, revision);
    }

    /**
     * Opens an already-published snapshot namespace for the current save
     * revision without creating a manifest or opening the source database.
     */
    public static Optional<WorldDataSnapshot> openExisting(
            RenderDataCacheStore cacheStore,
            Path savePath
    ) {
        Objects.requireNonNull(cacheStore, "cacheStore is required");
        Objects.requireNonNull(savePath, "savePath is required");
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        return cacheStore.find(revision)
                .map(ignored -> new WorldDataSnapshot(cacheStore, revision));
    }

    /**
     * Opens or creates the derived snapshot namespace for the current save
     * revision. No JDBC connection to the source save is retained.
     */
    public static Optional<WorldDataSnapshot> openOrCreate(
            RenderDataCacheStore cacheStore,
            Path savePath
    ) {
        Objects.requireNonNull(cacheStore, "cacheStore is required");
        Objects.requireNonNull(savePath, "savePath is required");
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        if (cacheStore.find(revision).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WorldDataSnapshot(cacheStore, revision));
    }
    /**
     * Opens an already-published compatible revision without creating cache
     * metadata. Intended for lightweight Workstation status inspection.
     */
    public static Optional<WorldDataSnapshot> openExisting(
            RenderDataCacheStore cacheStore,
            Path savePath
    ) {
        Objects.requireNonNull(cacheStore, "cacheStore is required");
        Objects.requireNonNull(savePath, "savePath is required");
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        return cacheStore.find(revision).isPresent()
                ? Optional.of(new WorldDataSnapshot(cacheStore, revision))
                : Optional.empty();
    }


    public RenderDataCacheRevision revision() {
        return revision;
    }

    public String revisionHash() {
        return revision.revisionHash();
    }

    public Path savePath() {
        return revision.identity().normalizedSavePath();
    }

    public WorldSnapshotHeaderStore headerStore() {
        return headerStore;
    }

    public TerrainTileStore terrainStore() {
        return terrainStore;
    }

    public SurfaceTileStore surfaceStore() {
        return surfaceStore;
    }

    public WorldIndexCatalogStore indexCatalogStore() {
        return indexCatalogStore;
    }

    public MapRegionSnapshotStore mapRegionStore() {
        return mapRegionStore;
    }

    public UpperRockTileStore upperRockTileStore() {
        return upperRockTileStore;
    }

    public ResourceIndexStore resourceIndexStore() {
        return resourceIndexStore;
    }

    public WorldSnapshotPreparationSummaryStore preparationSummaryStore() {
        return preparationSummaryStore;
    }

    public RenderDataCacheStore cacheStore() {
        return cacheStore;
    }
}
