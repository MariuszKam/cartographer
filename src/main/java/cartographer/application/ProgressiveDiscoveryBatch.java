package cartographer.application;

import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Objects;

/** One incremental observed-world discovery result for a progressive session. */
public record ProgressiveDiscoveryBatch(
        List<MapChunkCoordinate> coordinates,
        boolean complete
) {
    public ProgressiveDiscoveryBatch {
        Objects.requireNonNull(coordinates, "coordinates are required");
        coordinates = List.copyOf(coordinates);
    }

    public static ProgressiveDiscoveryBatch completed() {
        return new ProgressiveDiscoveryBatch(List.of(), true);
    }
}
