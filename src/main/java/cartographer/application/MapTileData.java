package cartographer.application;

import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunkCoordinate;
import cartographer.render.RenderTileBounds;
import cartographer.save.MapChunkReadStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Detached immutable input data for one render-tile operation. */
public record MapTileData(
        RenderTileBounds requestedBounds,
        Optional<RenderTileBounds> effectiveWorldBounds,
        List<MapChunkCoordinate> mapChunks,
        Map<MapChunkCoordinate, TerrainHeightTile> terrainTiles,
        Map<MapChunkCoordinate, SurfaceCacheTile> surfaceTiles,
        Map<MapChunkCoordinate, MapChunkReadStatus> sourceStatuses,
        MapTileDataRequirement requirement
) {
    public MapTileData {
        Objects.requireNonNull(requestedBounds, "requestedBounds is required");
        Objects.requireNonNull(effectiveWorldBounds, "effectiveWorldBounds is required");
        Objects.requireNonNull(mapChunks, "mapChunks is required");
        Objects.requireNonNull(terrainTiles, "terrainTiles is required");
        Objects.requireNonNull(surfaceTiles, "surfaceTiles is required");
        Objects.requireNonNull(sourceStatuses, "sourceStatuses is required");
        Objects.requireNonNull(requirement, "requirement is required");

        mapChunks = List.copyOf(mapChunks);
        terrainTiles = immutableOrderedMap(terrainTiles);
        surfaceTiles = immutableOrderedMap(surfaceTiles);
        sourceStatuses = immutableOrderedMap(sourceStatuses);

        if (effectiveWorldBounds.isEmpty() && !mapChunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "out-of-world tile data cannot contain mapchunks"
            );
        }
        if (!requirement.surfaceRequired() && !surfaceTiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "terrain-only tile data cannot contain Surface tiles"
            );
        }
    }

    public boolean terrainComplete() {
        return terrainTiles.keySet().containsAll(mapChunks);
    }

    public boolean surfaceComplete() {
        return !requirement.surfaceRequired()
                || surfaceTiles.keySet().containsAll(mapChunks);
    }

    public boolean completeForRequirement() {
        return terrainComplete() && surfaceComplete();
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
