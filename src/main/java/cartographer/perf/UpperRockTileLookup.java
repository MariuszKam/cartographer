package cartographer.perf;

import java.util.Objects;

/** Result of reading one optional PF-2.4 UPPER_ROCK tile. */
public record UpperRockTileLookup(Status status, UpperRockTile tile) {
    public enum Status {
        HIT,
        MISS,
        CORRUPT
    }

    public UpperRockTileLookup {
        Objects.requireNonNull(status, "status is required");
        if (status == Status.HIT && tile == null) {
            throw new IllegalArgumentException("a hit requires a ROCK tile");
        }
        if (status != Status.HIT && tile != null) {
            throw new IllegalArgumentException(
                    "non-hit cannot contain a ROCK tile"
            );
        }
    }

    public static UpperRockTileLookup hit(UpperRockTile tile) {
        return new UpperRockTileLookup(
                Status.HIT,
                Objects.requireNonNull(tile, "tile is required")
        );
    }

    public static UpperRockTileLookup miss() {
        return new UpperRockTileLookup(Status.MISS, null);
    }

    public static UpperRockTileLookup corrupt() {
        return new UpperRockTileLookup(Status.CORRUPT, null);
    }

}
