package cartographer.save;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;

import java.util.Objects;
import java.util.Optional;

/**
 * Strong exact-read result for one requested authoritative MAPCHUNK coordinate.
 *
 * <p>Source absence is intentionally distinct from a present row whose
 * payload cannot be decoded, and from a request that did not reach an
 * authoritative terminal conclusion.</p>
 */
public record MapChunkReadResult(
        MapChunkCoordinate coordinate,
        MapChunkReadStatus status,
        MapChunk mapChunk,
        String detail
) {

    public MapChunkReadResult {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(status, "status is required");
        detail = detail == null ? "" : detail.trim();

        if (status == MapChunkReadStatus.PRESENT_DECODED) {
            Objects.requireNonNull(
                    mapChunk,
                    "decoded result requires a mapchunk"
            );
            if (!coordinate.equals(mapChunk.coordinate())) {
                throw new IllegalArgumentException(
                        "decoded mapchunk coordinate does not match result coordinate"
                );
            }
        } else if (mapChunk != null) {
            throw new IllegalArgumentException(
                    "only PRESENT_DECODED may contain a mapchunk"
            );
        }
    }

    public static MapChunkReadResult decoded(MapChunk mapChunk) {
        Objects.requireNonNull(mapChunk, "mapChunk is required");
        return new MapChunkReadResult(
                mapChunk.coordinate(),
                MapChunkReadStatus.PRESENT_DECODED,
                mapChunk,
                ""
        );
    }

    public static MapChunkReadResult absent(MapChunkCoordinate coordinate) {
        return new MapChunkReadResult(
                coordinate,
                MapChunkReadStatus.ABSENT,
                null,
                ""
        );
    }

    public static MapChunkReadResult unreadable(
            MapChunkCoordinate coordinate,
            String detail
    ) {
        return new MapChunkReadResult(
                coordinate,
                MapChunkReadStatus.PRESENT_UNREADABLE,
                null,
                detail
        );
    }

    public static MapChunkReadResult notCompleted(
            MapChunkCoordinate coordinate,
            String detail
    ) {
        return new MapChunkReadResult(
                coordinate,
                MapChunkReadStatus.NOT_COMPLETED,
                null,
                detail
        );
    }

    public Optional<MapChunk> decodedMapChunk() {
        return Optional.ofNullable(mapChunk);
    }
}
