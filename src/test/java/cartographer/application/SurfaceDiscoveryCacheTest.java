package cartographer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cartographer.model.WorldPosition;
import cartographer.resource.ObservedSurfaceResourceCatalogBuilder;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.scanner.SurfaceObjectCompactScanResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SurfaceDiscoveryCacheTest {
    @Test
    void exactKeyHitAndEmptyResultAreSupported() {
        var cache = new SurfaceDiscoveryCache(2);
        var key = key("world.vcdbs", 256, 100.5, 200.5);
        var result = resultAt(100.5, 200.5);

        cache.put(key, result);

        assertSame(result, cache.get(key).orElseThrow());
        assertTrue(result.observedResources().resources().isEmpty());
    }

    @Test
    void radiusAndExactCenterDifferencesMiss() {
        var cache = new SurfaceDiscoveryCache(2);
        cache.put(key("world.vcdbs", 256, 100.1, 200.5), resultAt(100.1, 200.5));

        assertTrue(cache.get(key("world.vcdbs", 256, 100.1, 200.5)).isPresent());
        assertTrue(cache.get(key("world.vcdbs", 512, 100.1, 200.5)).isEmpty());
        assertTrue(cache.get(key("world.vcdbs", 256, 100.9, 200.5)).isEmpty());
    }

    @Test
    void pathsAreNormalizedAndDifferentSavesMiss() {
        var cache = new SurfaceDiscoveryCache(2);
        var normalized = key(Path.of(".", "saves", "..", "world.vcdbs"), 256, 1, 2);
        cache.put(normalized, resultAt(1, 2));

        assertTrue(cache.get(key(Path.of("world.vcdbs"), 256, 1, 2)).isPresent());
        assertTrue(cache.get(key("other.vcdbs", 256, 1, 2)).isEmpty());
    }

    @Test
    void lruEvictsLeastRecentlyUsedEntry() {
        var cache = new SurfaceDiscoveryCache(2);
        var a = key("a.vcdbs", 1, 1, 1);
        var b = key("b.vcdbs", 1, 1, 1);
        var c = key("c.vcdbs", 1, 1, 1);
        cache.put(a, resultAt(1, 1));
        cache.put(b, resultAt(1, 1));
        cache.get(a);
        cache.put(c, resultAt(1, 1));

        assertTrue(cache.get(a).isPresent());
        assertTrue(cache.get(c).isPresent());
        assertTrue(cache.get(b).isEmpty());
    }

    @Test
    void clearRemovesEntriesAndInvalidCapacityIsRejected() {
        var cache = new SurfaceDiscoveryCache(2);
        cache.put(key("world.vcdbs", 1, 1, 1), resultAt(1, 1));
        cache.clear();

        assertEquals(0, cache.size());
        assertFalse(cache.get(key("world.vcdbs", 1, 1, 1)).isPresent());
        assertThrows(IllegalArgumentException.class, () -> new SurfaceDiscoveryCache(0));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceDiscoveryCache(-1));
    }

    @Test
    void nonFiniteCenterIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> key("world.vcdbs", 1, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class,
                () -> key("world.vcdbs", 1, 1, Double.POSITIVE_INFINITY));
    }

    @Test
    void resultCenterMustMatchCacheKey() {
        var cache = new SurfaceDiscoveryCache(1);
        assertThrows(IllegalArgumentException.class,
                () -> cache.put(key("world.vcdbs", 1, 10, 20), emptyResult()));
    }

    private SurfaceDiscoveryCacheKey key(String path, int radius, double x, double z) {
        return key(Path.of(path), radius, x, z);
    }

    private SurfaceDiscoveryCacheKey key(Path path, int radius, double x, double z) {
        return new SurfaceDiscoveryCacheKey(path, radius, x, z);
    }

    private DiscoverObservedSurfaceResourcesResult emptyResult() {
        return resultAt(0, 0);
    }

    private DiscoverObservedSurfaceResourcesResult resultAt(double x, double z) {
        var candidates = new SurfaceObjectCandidateCatalogBuilder().build(java.util.Map.of());
        return new DiscoverObservedSurfaceResourcesResult(
                new WorldPosition(x, 0, z),
                candidates,
                0,
                0,
                new SelectiveChunkStreamStats(0, 0, 0, 0, 0, 0, 0, 0),
                SurfaceObjectCompactScanResult.empty(),
                new ObservedSurfaceResourceCatalogBuilder().build(candidates, SurfaceObjectCompactScanResult.empty()),
                new ReadDiagnostics(),
                new ReadDiagnostics()
        );
    }
}
