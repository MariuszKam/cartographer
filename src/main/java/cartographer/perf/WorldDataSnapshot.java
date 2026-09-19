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
    private final TerrainTileStore terrainStore;
    private final SurfaceTileStore surfaceStore;

    private WorldDataSnapshot(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore is required");
        this.revision = Objects.requireNonNull(revision, "revision is required");
        this.terrainStore = new TerrainTileStore(cacheStore, revision);
        this.surfaceStore = new SurfaceTileStore(cacheStore, revision);
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

    public RenderDataCacheRevision revision() {
        return revision;
    }

    public String revisionHash() {
        return revision.revisionHash();
    }

    public Path savePath() {
        return revision.identity().normalizedSavePath();
    }

    public TerrainTileStore terrainStore() {
        return terrainStore;
    }

    public SurfaceTileStore surfaceStore() {
        return surfaceStore;
    }

    public RenderDataCacheStore cacheStore() {
        return cacheStore;
    }
}
