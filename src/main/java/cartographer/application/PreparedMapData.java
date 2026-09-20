package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderOptions;
import cartographer.save.ReadDiagnostics;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable operation result containing reusable compact map inputs.
 *
 * <p>No SaveSession/JDBC resource or decoded source chunk is retained.</p>
 */
public record PreparedMapData(
        WorldMetadata metadata,
        WorldPosition player,
        WorldPosition center,
        RenderOptions options,
        MapTerrainPreparation terrain,
        PreparedSurfaceData surface,
        Map<Integer, BlockInfo> registry,
        ReadDiagnostics mapChunkDiagnostics,
        ReadDiagnostics chunkDiagnostics,
        RenderDataCacheReport renderDataCacheReport
) {
    public PreparedMapData {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(options, "options are required");
        Objects.requireNonNull(terrain, "terrain is required");
        Objects.requireNonNull(surface, "surface is required");
        registry = Map.copyOf(Objects.requireNonNull(registry, "registry is required"));
        Objects.requireNonNull(mapChunkDiagnostics, "mapChunkDiagnostics is required");
        Objects.requireNonNull(chunkDiagnostics, "chunkDiagnostics is required");
        Objects.requireNonNull(renderDataCacheReport, "renderDataCacheReport is required");
    }
}
