package cartographer.save;

import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * One bounded page of authoritative MAPCHUNK coordinate discovery.
 *
 * <p>The cursor is the last packed source position read, including rows
 * filtered out of the accepted main-world coordinate list. This guarantees
 * keyset discovery always advances through the source table.</p>
 */
public record ObservedMapChunkCoordinatePage(
        List<MapChunkCoordinate> coordinates,
        OptionalLong lastPosition,
        int rowsScanned,
        boolean complete
) {
    public ObservedMapChunkCoordinatePage {
        Objects.requireNonNull(coordinates, "coordinates are required");
        Objects.requireNonNull(lastPosition, "lastPosition is required");
        coordinates = List.copyOf(coordinates);
        if (rowsScanned < 0) {
            throw new IllegalArgumentException(
                    "rowsScanned must not be negative"
            );
        }
        if (coordinates.size() > rowsScanned) {
            throw new IllegalArgumentException(
                    "accepted coordinates cannot exceed scanned rows"
            );
        }
        if (rowsScanned == 0 && lastPosition.isPresent()) {
            throw new IllegalArgumentException(
                    "empty page cannot expose a source cursor"
            );
        }
        if (rowsScanned > 0 && lastPosition.isEmpty()) {
            throw new IllegalArgumentException(
                    "non-empty page requires a source cursor"
            );
        }
    }
}
