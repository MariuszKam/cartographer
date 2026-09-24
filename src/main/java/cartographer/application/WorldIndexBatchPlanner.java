package cartographer.application;

import cartographer.model.MapChunkCoordinate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Groups observed mapchunks into deterministic bounded spatial batches.
 *
 * <p>The default 16x16 mapchunk buckets keep Surface accumulator/planner state
 * bounded while allowing source chunk reads to aggregate across neighboring
 * mapchunks.</p>
 */
public final class WorldIndexBatchPlanner {
    public static final int DEFAULT_TILE_SPAN = 16;

    private final int tileSpan;

    public WorldIndexBatchPlanner() {
        this(DEFAULT_TILE_SPAN);
    }

    public WorldIndexBatchPlanner(int tileSpan) {
        if (tileSpan <= 0) {
            throw new IllegalArgumentException("tileSpan must be positive");
        }
        this.tileSpan = tileSpan;
    }

    public List<List<MapChunkCoordinate>> plan(
            Collection<MapChunkCoordinate> coordinates
    ) {
        Objects.requireNonNull(coordinates, "coordinates are required");
        LinkedHashSet<MapChunkCoordinate> unique = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            unique.add(Objects.requireNonNull(
                    coordinate,
                    "coordinates cannot contain null"
            ));
        }

        List<MapChunkCoordinate> ordered = unique.stream()
                .sorted(Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x))
                .toList();

        Map<Bucket, List<MapChunkCoordinate>> buckets = new LinkedHashMap<>();
        for (MapChunkCoordinate coordinate : ordered) {
            Bucket bucket = new Bucket(
                    Math.floorDiv(coordinate.x(), tileSpan),
                    Math.floorDiv(coordinate.z(), tileSpan)
            );
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>())
                    .add(coordinate);
        }

        return buckets.values().stream()
                .map(List::copyOf)
                .toList();
    }

    private record Bucket(int x, int z) {
    }
}
