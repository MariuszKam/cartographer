package cartographer.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Small access-ordered cache for successful discovery results only. */
public final class SurfaceDiscoveryCache {
    private final int capacity;
    private final Map<SurfaceDiscoveryCacheKey, DiscoverObservedSurfaceResourcesResult> entries;

    public SurfaceDiscoveryCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<SurfaceDiscoveryCacheKey, DiscoverObservedSurfaceResourcesResult> eldest
            ) {
                return size() > SurfaceDiscoveryCache.this.capacity;
            }
        };
    }

    public Optional<DiscoverObservedSurfaceResourcesResult> get(SurfaceDiscoveryCacheKey key) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(key, "key is required")));
    }

    public void put(
            SurfaceDiscoveryCacheKey key,
            DiscoverObservedSurfaceResourcesResult result
    ) {
        SurfaceDiscoveryCacheKey checkedKey = Objects.requireNonNull(key, "key is required");
        DiscoverObservedSurfaceResourcesResult checkedResult = Objects.requireNonNull(
                result, "result is required");
        if (Double.compare(checkedKey.centerX(), checkedResult.center().x()) != 0
                || Double.compare(checkedKey.centerZ(), checkedResult.center().z()) != 0) {
            throw new IllegalArgumentException("cache key center does not match discovery result");
        }
        entries.put(checkedKey, checkedResult);
    }

    public void clear() {
        entries.clear();
    }

}
