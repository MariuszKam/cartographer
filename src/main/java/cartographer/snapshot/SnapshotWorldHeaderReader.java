package cartographer.snapshot;

import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotHeader;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Source-free snapshot reader for the small revision-scoped world header. */
public final class SnapshotWorldHeaderReader {
    private final RenderDataCacheStore cacheStore;

    public SnapshotWorldHeaderReader(RenderDataCacheStore cacheStore) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public Optional<WorldSnapshotHeader> read(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");
        try {
            return WorldDataSnapshot.openOrCreate(cacheStore, savePath)
                    .flatMap(snapshot -> snapshot.headerStore().read());
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            return Optional.empty();
        }
    }
}
