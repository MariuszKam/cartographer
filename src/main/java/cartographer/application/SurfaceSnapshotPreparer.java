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
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTile;
import cartographer.scanner.SurfaceTileDiagnosticSummary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SurfaceSnapshotPreparer {
    private final VcdbsReader reader;
    private final WorldIndexBatchPlanner batchPlanner;
    private final SurfaceFallbackChunkPlanner fallbackPlanner =
            new SurfaceFallbackChunkPlanner();

    SurfaceSnapshotPreparer(
            VcdbsReader reader,
            WorldIndexBatchPlanner batchPlanner
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.batchPlanner = Objects.requireNonNull(
                batchPlanner,
                "batchPlanner is required"
        );
    }

    Result prepare(
            SaveSession session,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            TerrainTileStore terrainStore,
            SurfaceTileStore surfaceStore,
            List<MapChunkCoordinate> observed,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(terrainStore, "terrainStore is required");
        Objects.requireNonNull(surfaceStore, "surfaceStore is required");
        Objects.requireNonNull(observed, "observed is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");

        Counters counters = new Counters();
        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(observed);
        progress.start("Indexing snapshot");
        for (int index = 0; index < batches.size(); index++) {
            indexBatch(
                    session,
                    metadata,
                    registry,
                    terrainStore,
                    surfaceStore,
                    batches.get(index),
                    diagnostics,
                    counters,
                    progress
            );
            progress.progress("Indexing snapshot", index + 1, batches.size());
        }
        progress.done("Snapshot indexing complete");

        return new Result(
                coverageComplete(surfaceStore, observed, metadata),
                counters.hits,
                counters.published,
                counters.skippedIncomplete
        );
    }

    private void indexBatch(
            SaveSession session,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            TerrainTileStore terrainStore,
            SurfaceTileStore surfaceStore,
            List<MapChunkCoordinate> batch,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        Map<MapChunkCoordinate, SurfaceTileLookup> existing =
                surfaceStore.read(batch);
        List<MapChunkCoordinate> missing = new ArrayList<>();
        for (MapChunkCoordinate coordinate : batch) {
            SurfaceTileLookup lookup = existing.getOrDefault(
                    coordinate,
                    SurfaceTileLookup.miss()
            );
            if (lookup.status() == SurfaceTileLookup.Status.HIT
                    && lookup.tile().matchesWorld(metadata)) {
                counters.hits++;
            } else {
                missing.add(coordinate);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        Map<MapChunkCoordinate, TerrainTileLookup> terrain =
                terrainStore.read(missing);
        List<MapChunkCoordinate> ready = new ArrayList<>();
        List<TerrainHeightTile> heightTiles = new ArrayList<>();
        for (MapChunkCoordinate coordinate : missing) {
            TerrainTileLookup lookup = terrain.getOrDefault(
                    coordinate,
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                ready.add(coordinate);
                heightTiles.add(lookup.tile());
            } else {
                counters.skippedIncomplete++;
            }
        }
        if (ready.isEmpty()) {
            return;
        }

        SurfaceStreamingSession surface = SurfaceStreamingSession.beginForMapChunks(
                metadata,
                ready,
                registry,
                true,
                true
        );
        heightTiles.forEach(surface::acceptMapChunk);
        SurfaceRainHeightPlan plan = surface.finishPlanning();

        if (!plan.chunkPositions().isEmpty()) {
            reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    plan.chunkPositions(),
                    diagnostics,
                    surface::acceptFastChunk,
                    progress
            );
        }

        List<MapChunkCoordinate> fallbackMapChunks = surface.fallbackMapChunks();
        List<cartographer.model.ChunkPosition> fallbackPositions =
                fallbackPlanner.plan(metadata, fallbackMapChunks);
        if (!fallbackPositions.isEmpty()) {
            reader.forEachSurfaceChunkByPositionAdaptive(
                    session,
                    fallbackPositions,
                    diagnostics,
                    surface::acceptFallbackChunk,
                    progress
            );
        }

        SurfaceRainHeightScanResult result = surface.finish();
        Set<MapChunkCoordinate> fallback = new HashSet<>(
                result.fallbackMapChunks()
        );
        List<SurfaceCacheTile> publish = new ArrayList<>();

        for (MapChunkCoordinate coordinate : ready) {
            try {
                SurfaceTile tile = result.surface().tileAt(
                        result.surface().layout().tileIndex(
                                coordinate.x(),
                                coordinate.z()
                        )
                );
                boolean fallbackMode = fallback.contains(coordinate);
                SurfaceTileDiagnosticSummary summary = fallbackMode
                        ? result.fallbackDiagnosticsByMapChunk().get(coordinate)
                        : null;
                if (fallbackMode && summary == null) {
                    counters.skippedIncomplete++;
                    continue;
                }
                publish.add(SurfaceCacheTile.fromComplete(
                        tile,
                        metadata,
                        fallbackMode
                                ? SurfaceCacheTile.SourceMode.FALLBACK
                                : SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                        fallbackMode
                                ? summary.columnsScanned()
                                : countActiveConsidered(tile),
                        fallbackMode ? summary.emptyColumns() : 0,
                        fallbackMode
                                ? summary.liquidUnavailableColumns()
                                : 0
                ));
            } catch (RuntimeException exception) {
                counters.skippedIncomplete++;
            }
        }

        if (!publish.isEmpty()) {
            surfaceStore.publish(publish);
            counters.published = Math.addExact(
                    counters.published,
                    publish.size()
            );
        }
    }

    private boolean coverageComplete(
            SurfaceTileStore store,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(coordinates);
        for (List<MapChunkCoordinate> batch : batches) {
            Map<MapChunkCoordinate, SurfaceTileLookup> lookups = store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                SurfaceTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        SurfaceTileLookup.miss()
                );
                if (lookup.status() != SurfaceTileLookup.Status.HIT
                        || !lookup.tile().matchesWorld(metadata)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countActiveConsidered(SurfaceTile tile) {
        int count = 0;
        for (int localZ = 0; localZ < tile.height(); localZ++) {
            for (int localX = 0; localX < tile.width(); localX++) {
                if (tile.isActive(localX, localZ)
                        && tile.isConsidered(localX, localZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    record Result(
            boolean coverageComplete,
            int hits,
            int published,
            int skippedIncomplete
    ) {
    }

    private static final class Counters {
        private int hits;
        private int published;
        private int skippedIncomplete;
    }
}
