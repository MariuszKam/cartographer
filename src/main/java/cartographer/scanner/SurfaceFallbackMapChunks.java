package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SurfaceFallbackMapChunks {

    private static final Comparator<MapChunkCoordinate> ORDER =
            Comparator.comparingInt(MapChunkCoordinate::z)
                    .thenComparingInt(MapChunkCoordinate::x);

    public List<MapChunkCoordinate> collect(
            Collection<MapChunkCoordinate> plannedMapChunks,
            Collection<MapChunkCoordinate> deliveredMapChunks,
            RainHeightSurfacePlan rainPlan,
            RainHeightSurfaceScanResult scanResult
    ) {
        Objects.requireNonNull(plannedMapChunks, "plannedMapChunks is required");
        Objects.requireNonNull(deliveredMapChunks, "deliveredMapChunks is required");
        Objects.requireNonNull(rainPlan, "rainPlan is required");
        Objects.requireNonNull(scanResult, "scanResult is required");

        Set<MapChunkCoordinate> planned = validatedSet(
                plannedMapChunks,
                "plannedMapChunks"
        );
        Set<MapChunkCoordinate> delivered = validatedSet(
                deliveredMapChunks,
                "deliveredMapChunks"
        );
        Set<MapChunkCoordinate> fallback = new HashSet<>(planned);
        fallback.removeAll(delivered);
        fallback.addAll(rainPlan.fallbackMapChunks());
        for (RainHeightSurfaceTarget target : scanResult.unresolvedTargets()) {
            fallback.add(target.mapChunkCoordinate());
        }

        return fallback.stream()
                .sorted(ORDER)
                .toList();
    }

    private Set<MapChunkCoordinate> validatedSet(
            Collection<MapChunkCoordinate> coordinates,
            String name
    ) {
        Set<MapChunkCoordinate> result = new HashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            result.add(
                    Objects.requireNonNull(
                            coordinate,
                            name + " cannot contain null"
                    )
            );
        }
        return result;
    }
}
