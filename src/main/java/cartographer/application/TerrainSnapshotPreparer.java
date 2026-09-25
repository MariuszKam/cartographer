package cartographer.application;

import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileLookup;
import cartographer.cache.TerrainTileStore;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.progress.ProgressReporter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.WorldIndexCatalogStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TerrainSnapshotPreparer {
    private static final int TERRAIN_BATCH_SIZE = 128;

    private final VcdbsReader reader;

    TerrainSnapshotPreparer(VcdbsReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
    }

    Result prepare(
            SaveSession session,
            TerrainTileStore terrainStore,
            WorldIndexCatalogStore indexStore,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(terrainStore, "terrainStore is required");
        Objects.requireNonNull(indexStore, "indexStore is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");

        Counters counters = new Counters();
        progress.start("Checking observed mapchunk coverage");
        boolean catalogWasComplete = indexStore.mapChunkScanComplete();
        if (!catalogWasComplete) {
            discoverObservedMapChunks(
                    session,
                    terrainStore,
                    indexStore,
                    diagnostics,
                    counters,
                    progress
            );
            indexStore.markMapChunkScanComplete();
        }

        List<MapChunkCoordinate> observed = indexStore.observedMapChunks();
        if (catalogWasComplete) {
            repairTerrainCoverage(
                    session,
                    terrainStore,
                    observed,
                    diagnostics,
                    counters,
                    progress
            );
        }

        boolean complete = coverageComplete(terrainStore, observed);
        progress.done(
                complete
                        ? "Terrain coverage ready"
                        : "Terrain coverage partial"
        );
        return new Result(
                observed,
                indexStore.mapChunkScanComplete(),
                complete,
                counters.hits,
                counters.published
        );
    }

    private void discoverObservedMapChunks(
            SaveSession session,
            TerrainTileStore terrainStore,
            WorldIndexCatalogStore indexStore,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        List<MapChunkCoordinate> observedBuffer =
                new ArrayList<>(TERRAIN_BATCH_SIZE);
        List<MapChunk> terrainBuffer =
                new ArrayList<>(TERRAIN_BATCH_SIZE);
        reader.forEachObservedMapChunk(
                session,
                diagnostics,
                coordinate -> {
                    observedBuffer.add(coordinate);
                    if (observedBuffer.size() >= TERRAIN_BATCH_SIZE) {
                        indexStore.recordObserved(List.copyOf(observedBuffer));
                        observedBuffer.clear();
                    }
                },
                mapChunk -> {
                    terrainBuffer.add(mapChunk);
                    if (terrainBuffer.size() >= TERRAIN_BATCH_SIZE) {
                        publishDiscoveryTerrainBatch(
                                terrainStore,
                                terrainBuffer,
                                counters
                        );
                    }
                },
                progress
        );
        if (!observedBuffer.isEmpty()) {
            indexStore.recordObserved(List.copyOf(observedBuffer));
            observedBuffer.clear();
        }
        publishDiscoveryTerrainBatch(terrainStore, terrainBuffer, counters);
    }

    private void publishDiscoveryTerrainBatch(
            TerrainTileStore terrainStore,
            List<MapChunk> buffer,
            Counters counters
    ) {
        if (buffer.isEmpty()) {
            return;
        }
        List<MapChunkCoordinate> coordinates = buffer.stream()
                .map(MapChunk::coordinate)
                .toList();
        Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                terrainStore.read(coordinates);
        List<TerrainHeightTile> publish = new ArrayList<>();
        for (MapChunk mapChunk : buffer) {
            TerrainTileLookup lookup = lookups.getOrDefault(
                    mapChunk.coordinate(),
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                counters.hits++;
            } else {
                publish.add(TerrainHeightTile.from(mapChunk));
            }
        }
        if (!publish.isEmpty()) {
            terrainStore.publish(publish);
            counters.published = Math.addExact(
                    counters.published,
                    publish.size()
            );
        }
        buffer.clear();
    }

    private void repairTerrainCoverage(
            SaveSession session,
            TerrainTileStore terrainStore,
            List<MapChunkCoordinate> observed,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        for (int start = 0; start < observed.size(); start += TERRAIN_BATCH_SIZE) {
            List<MapChunkCoordinate> batch = observed.subList(
                    start,
                    Math.min(start + TERRAIN_BATCH_SIZE, observed.size())
            );
            Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                    terrainStore.read(batch);
            List<MapChunkCoordinate> missing = new ArrayList<>();
            for (MapChunkCoordinate coordinate : batch) {
                TerrainTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        TerrainTileLookup.miss()
                );
                if (lookup.status() == TerrainTileLookup.Status.HIT) {
                    counters.hits++;
                } else {
                    missing.add(coordinate);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }

            List<TerrainHeightTile> rebuilt = new ArrayList<>();
            reader.forEachMapChunkByCoordinate(
                    session,
                    missing,
                    diagnostics,
                    mapChunk -> rebuilt.add(TerrainHeightTile.from(mapChunk)),
                    progress
            );
            if (!rebuilt.isEmpty()) {
                terrainStore.publish(rebuilt);
                counters.published = Math.addExact(
                        counters.published,
                        rebuilt.size()
                );
            }
        }
    }

    private boolean coverageComplete(
            TerrainTileStore store,
            List<MapChunkCoordinate> coordinates
    ) {
        for (int start = 0; start < coordinates.size(); start += TERRAIN_BATCH_SIZE) {
            List<MapChunkCoordinate> batch = coordinates.subList(
                    start,
                    Math.min(start + TERRAIN_BATCH_SIZE, coordinates.size())
            );
            Map<MapChunkCoordinate, TerrainTileLookup> lookups = store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                if (lookups.getOrDefault(
                        coordinate,
                        TerrainTileLookup.miss()
                ).status() != TerrainTileLookup.Status.HIT) {
                    return false;
                }
            }
        }
        return true;
    }

    record Result(
            List<MapChunkCoordinate> observed,
            boolean catalogComplete,
            boolean coverageComplete,
            int hits,
            int published
    ) {
        Result {
            observed = List.copyOf(observed);
        }
    }

    private static final class Counters {
        private int hits;
        private int published;
    }
}
