package cartographer.render;

import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunkCoordinate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached immutable renderer input for one world-space tile.
 *
 * <p>This type contains no application orchestration state and no source/JDBC
 * diagnostics.</p>
 */
public record RenderTileData(
        RenderTileBounds requestedBounds,
        Optional<RenderTileBounds> effectiveWorldBounds,
        List<MapChunkCoordinate> mapChunks,
        Map<MapChunkCoordinate, TerrainHeightTile> terrainTiles,
        Map<MapChunkCoordinate, SurfaceCacheTile> surfaceTiles
) {
    public RenderTileData {
        Objects.requireNonNull(requestedBounds, "requestedBounds is required");
        Objects.requireNonNull(
                effectiveWorldBounds,
                "effectiveWorldBounds is required"
        );
        Objects.requireNonNull(mapChunks, "mapChunks is required");
        Objects.requireNonNull(terrainTiles, "terrainTiles is required");
        Objects.requireNonNull(surfaceTiles, "surfaceTiles is required");

        mapChunks = List.copyOf(mapChunks);
        terrainTiles = immutableOrderedMap(terrainTiles);
        surfaceTiles = immutableOrderedMap(surfaceTiles);

        if (effectiveWorldBounds.isEmpty() && !mapChunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "out-of-world render data cannot contain mapchunks"
            );
        }
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
