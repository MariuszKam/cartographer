package cartographer.ui.workstation;

import cartographer.render.RenderLod;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Bounded presentation cache for progressive rendered map tiles.
 *
 * <p>The cache admits only tiles that are still relevant to the current
 * viewport. It also tracks requests already emitted by the viewport so layout
 * pulses do not repeatedly enqueue the same missing raster.</p>
 */
final class WorldMapViewportTileCache<T> {
    private final int capacity;
    private final Map<RenderTileKey, T> tiles =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Set<RenderTileKey> requested = new HashSet<>();

    private List<RenderTileCoordinate> visibleOrder = List.of();
    private List<RenderTileCoordinate> prefetchOrder = List.of();
    private Set<RenderTileCoordinate> visible = Set.of();
    private Set<RenderTileCoordinate> prefetch = Set.of();
    private RenderLod currentLod = RenderLod.fullDetail();

    WorldMapViewportTileCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    void updateDemand(WorldMapViewportDemand demand) {
        Objects.requireNonNull(demand, "demand is required");
        visibleOrder = demand.visible();
        prefetchOrder = demand.prefetch();
        visible = Set.copyOf(visibleOrder);
        prefetch = Set.copyOf(prefetchOrder);
        currentLod = demand.lod();

        Set<RenderTileKey> relevantKeys = new HashSet<>();
        addKeys(relevantKeys, demand.visible(), currentLod);
        addKeys(relevantKeys, demand.prefetch(), currentLod);
        requested.retainAll(relevantKeys);
        trimToCapacity();
    }

    WorldMapViewportDemand missingDemand() {
        List<RenderTileCoordinate> missingVisible = missing(visibleOrder);
        List<RenderTileCoordinate> missingPrefetch = missing(prefetchOrder);
        return new WorldMapViewportDemand(
                missingVisible,
                missingPrefetch,
                currentLod
        );
    }

    void clearPendingRequests() {
        requested.clear();
    }

    boolean accept(RenderTileKey key, T value) {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(value, "value is required");
        requested.remove(key);

        RenderTileCoordinate coordinate = key.coordinate();
        if (!visible.contains(coordinate) && !prefetch.contains(coordinate)) {
            return false;
        }

        RenderTileKey bestExisting = bestKeyFor(coordinate);
        if (bestExisting != null && !key.equals(currentKey(coordinate))) {
            int existingDistance = lodDistance(bestExisting.lod(), currentLod);
            int candidateDistance = lodDistance(key.lod(), currentLod);
            if (existingDistance <= candidateDistance) {
                return false;
            }
        }

        removeCoordinate(coordinate);
        tiles.put(key, value);
        trimToCapacity();
        return true;
    }

    T best(RenderTileKey desired) {
        Objects.requireNonNull(desired, "desired is required");
        T exact = tiles.get(desired);
        if (exact != null) {
            return exact;
        }
        RenderTileKey bestKey = bestKeyFor(desired.coordinate());
        return bestKey == null ? null : tiles.get(bestKey);
    }

    void clear() {
        tiles.clear();
        requested.clear();
    }

    int size() {
        return tiles.size();
    }

    private List<RenderTileCoordinate> missing(
            List<RenderTileCoordinate> coordinates
    ) {
        List<RenderTileCoordinate> missing = new ArrayList<>();
        for (RenderTileCoordinate coordinate : coordinates) {
            RenderTileKey key = currentKey(coordinate);
            if (!tiles.containsKey(key) && requested.add(key)) {
                missing.add(coordinate);
            }
        }
        return List.copyOf(missing);
    }

    private RenderTileKey currentKey(RenderTileCoordinate coordinate) {
        return new RenderTileKey(coordinate, currentLod);
    }

    private RenderTileKey bestKeyFor(RenderTileCoordinate coordinate) {
        RenderTileKey bestKey = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RenderTileKey candidate : tiles.keySet()) {
            if (!candidate.coordinate().equals(coordinate)) {
                continue;
            }
            int distance = lodDistance(candidate.lod(), currentLod);
            if (distance < bestDistance) {
                bestKey = candidate;
                bestDistance = distance;
            }
        }
        return bestKey;
    }

    private int lodDistance(RenderLod first, RenderLod second) {
        return Math.abs(first.level() - second.level());
    }

    private void removeCoordinate(RenderTileCoordinate coordinate) {
        tiles.keySet().removeIf(
                key -> key.coordinate().equals(coordinate)
        );
    }

    private void trimToCapacity() {
        while (tiles.size() > capacity) {
            RenderTileKey victim = firstMatching(
                    key -> !visible.contains(key.coordinate())
                            && !prefetch.contains(key.coordinate())
            );
            if (victim == null) {
                victim = firstMatching(
                        key -> prefetch.contains(key.coordinate())
                                && !visible.contains(key.coordinate())
                );
            }
            if (victim == null) {
                victim = tiles.keySet().iterator().next();
            }
            tiles.remove(victim);
        }
    }

    private RenderTileKey firstMatching(
            Predicate<RenderTileKey> predicate
    ) {
        for (RenderTileKey key : tiles.keySet()) {
            if (predicate.test(key)) {
                return key;
            }
        }
        return null;
    }

    private void addKeys(
            Set<RenderTileKey> target,
            List<RenderTileCoordinate> coordinates,
            RenderLod lod
    ) {
        for (RenderTileCoordinate coordinate : coordinates) {
            target.add(new RenderTileKey(coordinate, lod));
        }
    }
}
