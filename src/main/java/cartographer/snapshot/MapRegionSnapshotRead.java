package cartographer.snapshot;

import java.util.List;
import java.util.Objects;

/** Current persisted mapregion snapshot state for one save revision. */
public record MapRegionSnapshotRead(
        boolean scanComplete,
        List<MapRegionSnapshotEntry> entries,
        int corruptRows
) {
    public MapRegionSnapshotRead {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries are required"));
        if (corruptRows < 0) {
            throw new IllegalArgumentException("corruptRows cannot be negative");
        }
    }

    public boolean healthyComplete() {
        return scanComplete && corruptRows == 0;
    }
}
