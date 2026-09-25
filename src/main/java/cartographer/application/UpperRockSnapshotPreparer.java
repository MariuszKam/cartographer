package cartographer.application;

import cartographer.geology.rock.RockCatalog;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.progress.ProgressReporter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.UpperRockTile;
import cartographer.snapshot.UpperRockTileBatchIndexer;
import cartographer.snapshot.UpperRockTileLookup;
import cartographer.snapshot.UpperRockTileStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class UpperRockSnapshotPreparer {
    private final VcdbsReader reader;
    private final WorldIndexBatchPlanner batchPlanner;

    UpperRockSnapshotPreparer(
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
            List<MapChunkCoordinate> observed,
            RockCatalog catalog,
            UpperRockTileStore store,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(observed, "observed is required");
        Objects.requireNonNull(catalog, "catalog is required");
        Objects.requireNonNull(store, "store is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");

        if (observed.isEmpty()) {
            return new Result(true, 0, 0);
        }
        if (catalog.rocks().isEmpty()) {
            progress.done(
                    "UPPER_ROCK snapshot unavailable: no natural rock registry"
            );
            return new Result(false, 0, 0);
        }

        Counters counters = new Counters();
        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(observed);
        progress.start("Indexing UPPER_ROCK snapshot");
        for (int index = 0; index < batches.size(); index++) {
            indexBatch(
                    session,
                    metadata,
                    batches.get(index),
                    catalog,
                    store,
                    diagnostics,
                    counters,
                    progress
            );
            progress.progress(
                    "Indexing UPPER_ROCK snapshot",
                    index + 1,
                    batches.size()
            );
        }
        progress.done("UPPER_ROCK snapshot indexing complete");
        return new Result(
                coverageComplete(store, observed, metadata),
                counters.hits,
                counters.published
        );
    }

    private void indexBatch(
            SaveSession session,
            WorldMetadata metadata,
            List<MapChunkCoordinate> batch,
            RockCatalog catalog,
            UpperRockTileStore store,
            ReadDiagnostics diagnostics,
            Counters counters,
            ProgressReporter progress
    ) {
        Map<MapChunkCoordinate, UpperRockTileLookup> existing = store.read(batch);
        List<MapChunkCoordinate> missing = new ArrayList<>();
        for (MapChunkCoordinate coordinate : batch) {
            UpperRockTileLookup lookup = existing.getOrDefault(
                    coordinate,
                    UpperRockTileLookup.miss()
            );
            if (lookup.status() == UpperRockTileLookup.Status.HIT
                    && lookup.tile().matchesWorld(metadata)) {
                counters.hits++;
            } else {
                missing.add(coordinate);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        UpperRockTileBatchIndexer indexer = new UpperRockTileBatchIndexer(
                metadata,
                missing,
                catalog
        );
        List<cartographer.model.ChunkPosition> positions = indexer.positions();
        if (!positions.isEmpty()) {
            reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    session,
                    positions,
                    indexer.wantedBlockIds(),
                    diagnostics,
                    indexer::accept,
                    progress
            );
        }

        List<UpperRockTile> publish = indexer.finish();
        if (!publish.isEmpty()) {
            store.publish(publish);
            counters.published = Math.addExact(
                    counters.published,
                    publish.size()
            );
        }
    }

    private boolean coverageComplete(
            UpperRockTileStore store,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(coordinates);
        for (List<MapChunkCoordinate> batch : batches) {
            Map<MapChunkCoordinate, UpperRockTileLookup> lookups = store.read(batch);
            for (MapChunkCoordinate coordinate : batch) {
                UpperRockTileLookup lookup = lookups.getOrDefault(
                        coordinate,
                        UpperRockTileLookup.miss()
                );
                if (lookup.status() != UpperRockTileLookup.Status.HIT
                        || !lookup.tile().matchesWorld(metadata)) {
                    return false;
                }
            }
        }
        return true;
    }

    record Result(boolean coverageComplete, int hits, int published) {
    }

    private static final class Counters {
        private int hits;
        private int published;
    }
}
