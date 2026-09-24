package cartographer.perf;

import java.util.Objects;

/** Result of reading one optional terrain artifact. */
public record TerrainTileLookup(Status status, TerrainHeightTile tile) {
    public enum Status {
        HIT,
        MISS,
        CORRUPT
    }

    public TerrainTileLookup {
        Objects.requireNonNull(status, "status is required");
        if (status == Status.HIT && tile == null) {
            throw new IllegalArgumentException("a hit requires a terrain tile");
        }
        if (status != Status.HIT && tile != null) {
            throw new IllegalArgumentException("a miss or corrupt result cannot contain a tile");
        }
    }

    public static TerrainTileLookup hit(TerrainHeightTile tile) {
        return new TerrainTileLookup(Status.HIT, Objects.requireNonNull(tile, "tile is required"));
    }

    public static TerrainTileLookup miss() {
        return new TerrainTileLookup(Status.MISS, null);
    }

    public static TerrainTileLookup corrupt() {
        return new TerrainTileLookup(Status.CORRUPT, null);
    }

}
