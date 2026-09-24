package cartographer.perf;

import java.util.Objects;

/** Result of reading one optional Surface cache artifact. */
public record SurfaceTileLookup(Status status, SurfaceCacheTile tile) {
    public enum Status { HIT, MISS, CORRUPT }

    public SurfaceTileLookup {
        Objects.requireNonNull(status, "status is required");
        if (status == Status.HIT && tile == null) throw new IllegalArgumentException("a hit requires a tile");
        if (status != Status.HIT && tile != null) throw new IllegalArgumentException("non-hit cannot contain a tile");
    }

    public static SurfaceTileLookup hit(SurfaceCacheTile tile) {
        return new SurfaceTileLookup(Status.HIT, Objects.requireNonNull(tile, "tile is required"));
    }

    public static SurfaceTileLookup miss() { return new SurfaceTileLookup(Status.MISS, null); }
    public static SurfaceTileLookup corrupt() { return new SurfaceTileLookup(Status.CORRUPT, null); }
}
