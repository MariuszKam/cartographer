package cartographer.render;

import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Objects;

/**
 * Defines the fixed spatial size and alignment of render tiles.
 *
 * <p>The span is deliberately configurable during the progressive-rendering
 * migration. Phase 0/5 benchmarks decide the production value; Phase 1 only
 * establishes deterministic coordinate semantics.</p>
 */
public record RenderTileLayout(
        int mapChunksPerSide
) {

    public RenderTileLayout {
        if (mapChunksPerSide <= 0) {
            throw new IllegalArgumentException("mapChunksPerSide must be positive");
        }
        if (mapChunksPerSide
                > Integer.MAX_VALUE / MapChunkCoordinate.SIZE_BLOCKS) {
            throw new IllegalArgumentException(
                    "mapChunksPerSide is too large"
            );
        }
    }

    public int worldBlocksPerSide() {
        return Math.multiplyExact(
                mapChunksPerSide,
                MapChunkCoordinate.SIZE_BLOCKS
        );
    }

    public RenderTileCoordinate coordinateFor(MapChunkCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        return new RenderTileCoordinate(
                Math.floorDiv(coordinate.x(), mapChunksPerSide),
                Math.floorDiv(coordinate.z(), mapChunksPerSide)
        );
    }

    public RenderTileCoordinate coordinateForWorld(double worldX, double worldZ) {
        return coordinateFor(MapChunkCoordinate.fromWorld(worldX, worldZ));
    }

    public RenderTileBounds boundsFor(RenderTileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        long firstMapChunkX = Math.multiplyExact(
                (long) coordinate.x(),
                mapChunksPerSide
        );
        long firstMapChunkZ = Math.multiplyExact(
                (long) coordinate.z(),
                mapChunksPerSide
        );
        long maxMapChunkXExclusive = Math.addExact(
                firstMapChunkX,
                mapChunksPerSide
        );
        long maxMapChunkZExclusive = Math.addExact(
                firstMapChunkZ,
                mapChunksPerSide
        );
        validateMapChunkInterval(firstMapChunkX, maxMapChunkXExclusive);
        validateMapChunkInterval(firstMapChunkZ, maxMapChunkZExclusive);

        long mapChunkSize = MapChunkCoordinate.SIZE_BLOCKS;
        return new RenderTileBounds(
                Math.multiplyExact(firstMapChunkX, mapChunkSize),
                Math.multiplyExact(firstMapChunkZ, mapChunkSize),
                Math.multiplyExact(maxMapChunkXExclusive, mapChunkSize),
                Math.multiplyExact(maxMapChunkZExclusive, mapChunkSize)
        );
    }

    public List<MapChunkCoordinate> mapChunksFor(RenderTileCoordinate coordinate) {
        return boundsFor(coordinate).intersectingMapChunks();
    }

    private static void validateMapChunkInterval(long min, long maxExclusive) {
        if (min < Integer.MIN_VALUE
                || maxExclusive > (long) Integer.MAX_VALUE + 1L) {
            throw new IllegalArgumentException(
                    "render tile exceeds mapchunk coordinate range"
            );
        }
    }
}
