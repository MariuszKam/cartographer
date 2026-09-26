package cartographer.application;

import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.SurfaceTileLookup;
import cartographer.cache.SurfaceTileStore;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileLookup;
import cartographer.cache.TerrainTileStore;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.progress.ProgressReporter;
import cartographer.render.RenderTileBounds;
import cartographer.save.MapChunkReadResult;
import cartographer.save.MapChunkReadStatus;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache-first loader for one progressive render tile.
 *
 * <p>The loader owns orchestration only. Terrain/Surface persistence remains
 * in the existing revision-scoped stores and authoritative source reads remain
 * on the caller-owned, thread-confined {@link SaveSession}.</p>
 */
public final class MapTileDataLoader {
    private final VcdbsReader reader;
    private final SurfaceSnapshotPreparer surfacePreparer;

    public MapTileDataLoader(
            VcdbsReader reader,
            WorldIndexBatchPlanner surfaceBatchPlanner
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.surfacePreparer = new SurfaceSnapshotPreparer(
                reader,
                Objects.requireNonNull(
                        surfaceBatchPlanner,
                        "surfaceBatchPlanner is required"
                )
        );
    }

    public MapTileData load(
            SaveSession session,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            TerrainTileStore terrainStore,
            SurfaceTileStore surfaceStore,
            RenderTileBounds requestedBounds,
            MapTileDataRequirement requirement,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(terrainStore, "terrainStore is required");
        Objects.requireNonNull(surfaceStore, "surfaceStore is required");
        Objects.requireNonNull(requestedBounds, "requestedBounds is required");
        Objects.requireNonNull(requirement, "requirement is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");

        Optional<RenderTileBounds> effective =
                requestedBounds.clipToWorld(metadata);
        if (effective.isEmpty()) {
            return new MapTileData(
                    requestedBounds,
                    Optional.empty(),
                    List.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    requirement
            );
        }

        List<MapChunkCoordinate> coordinates =
                effective.orElseThrow().intersectingMapChunks();
        TerrainLoadResult terrainResult = loadTerrain(
                session,
                terrainStore,
                coordinates,
                diagnostics,
                progress
        );

        LinkedHashMap<MapChunkCoordinate, SurfaceCacheTile> surface =
                new LinkedHashMap<>();
        if (requirement.surfaceRequired()) {
            surfacePreparer.prepare(
                    session,
                    metadata,
                    registry,
                    terrainStore,
                    surfaceStore,
                    coordinates,
                    diagnostics,
                    progress
            );
            Map<MapChunkCoordinate, SurfaceTileLookup> lookups =
                    surfaceStore.read(coordinates);
            for (MapChunkCoordinate coordinate : coordinates) {
                SurfaceTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        SurfaceTileLookup.miss()
                );
                if (lookup.status() == SurfaceTileLookup.Status.HIT
                        && lookup.tile().matchesWorld(metadata)) {
                    surface.put(coordinate, lookup.tile());
                }
            }
        }

        return new MapTileData(
                requestedBounds,
                effective,
                coordinates,
                terrainResult.tiles(),
                surface,
                terrainResult.sourceStatuses(),
                requirement
        );
    }

    private TerrainLoadResult loadTerrain(
            SaveSession session,
            TerrainTileStore terrainStore,
            List<MapChunkCoordinate> coordinates,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        LinkedHashMap<MapChunkCoordinate, TerrainHeightTile> terrain =
                new LinkedHashMap<>();
        LinkedHashMap<MapChunkCoordinate, MapChunkReadStatus> sourceStatuses =
                new LinkedHashMap<>();
        List<MapChunkCoordinate> sourceNeeded = new ArrayList<>();

        Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                terrainStore.read(coordinates);
        for (MapChunkCoordinate coordinate : coordinates) {
            TerrainTileLookup lookup = lookups.getOrDefault(
                    coordinate,
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                terrain.put(coordinate, lookup.tile());
            } else {
                sourceNeeded.add(coordinate);
            }
        }

        if (!sourceNeeded.isEmpty()) {
            List<TerrainHeightTile> publish = new ArrayList<>();
            reader.forEachMapChunkByCoordinateWithResults(
                    session,
                    sourceNeeded,
                    diagnostics,
                    result -> acceptSourceResult(
                            result,
                            terrain,
                            publish,
                            sourceStatuses
                    ),
                    progress
            );
            if (!publish.isEmpty()) {
                terrainStore.publish(publish);
            }
        }

        return new TerrainLoadResult(terrain, sourceStatuses);
    }

    private void acceptSourceResult(
            MapChunkReadResult result,
            Map<MapChunkCoordinate, TerrainHeightTile> terrain,
            List<TerrainHeightTile> publish,
            Map<MapChunkCoordinate, MapChunkReadStatus> sourceStatuses
    ) {
        sourceStatuses.put(result.coordinate(), result.status());
        result.decodedMapChunk().ifPresent(mapChunk -> {
            TerrainHeightTile tile = TerrainHeightTile.from(mapChunk);
            terrain.put(result.coordinate(), tile);
            publish.add(tile);
        });
    }

    private record TerrainLoadResult(
            Map<MapChunkCoordinate, TerrainHeightTile> tiles,
            Map<MapChunkCoordinate, MapChunkReadStatus> sourceStatuses
    ) {
    }
}
