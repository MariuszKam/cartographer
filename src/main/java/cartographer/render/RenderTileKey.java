package cartographer.render;

import java.util.Objects;

/** Unique progressive raster identity: spatial tile coordinate plus LOD. */
public record RenderTileKey(
        RenderTileCoordinate coordinate,
        RenderLod lod
) {
    public RenderTileKey {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(lod, "lod is required");
    }

    public static RenderTileKey fullDetail(
            RenderTileCoordinate coordinate
    ) {
        return new RenderTileKey(coordinate, RenderLod.fullDetail());
    }
}
