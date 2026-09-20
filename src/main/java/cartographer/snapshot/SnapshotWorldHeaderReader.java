package cartographer.snapshot;

import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldSnapshotHeader;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Source-free PF-2.6 reader for the small revision-scoped world header. */
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
            return Optional.empty();
        }
    }
}
