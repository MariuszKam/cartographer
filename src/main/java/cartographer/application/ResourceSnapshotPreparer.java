package cartographer.application;

import cartographer.index.ResourceBlockCatalog;
import cartographer.index.ResourceChunkBatchIndexer;
import cartographer.index.ResourceChunkIndexEntry;
import cartographer.index.ResourceChunkIndexLookup;
import cartographer.index.ResourceIndexStore;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.progress.ProgressReporter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ResourceSnapshotPreparer {
    private final VcdbsReader reader;
    private final WorldIndexBatchPlanner batchPlanner;

    ResourceSnapshotPreparer(
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
            ResourceBlockCatalog catalog,
            ResourceIndexStore store,
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

        int blocksCatalogued = catalog.blocks().size();
        store.publishBlockCatalog(catalog.blocks());

        if (catalog.isEmpty()) {
            if (!store.scanComplete()) {
                store.markScanComplete();
            }
            return new Result(true, blocksCatalogued, 0, 0, 0L);
        }

        Counters counters = new Counters();
        boolean markerInvalidated = !store.scanComplete();
        if (markerInvalidated) {
            store.markScanIncomplete();
        }

        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(observed);
        progress.start("Indexing resource snapshot");
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<ChunkPosition> positions = chunkPositions(
                    metadata,
                    batches.get(batchIndex)
            );
            Map<ChunkPosition, ResourceChunkIndexLookup> existing =
                    store.readCoverage(positions);
            List<ChunkPosition> missing = new ArrayList<>();
            for (ChunkPosition position : positions) {
                ResourceChunkIndexLookup lookup = existing.getOrDefault(
                        position,
                        ResourceChunkIndexLookup.miss()
                );
                if (lookup.status() == ResourceChunkIndexLookup.Status.HIT) {
                    counters.hits++;
                } else {
                    missing.add(position);
                }
            }

            if (!missing.isEmpty()) {
                if (!markerInvalidated) {
                    store.markScanIncomplete();
                    markerInvalidated = true;
                }
                ResourceChunkBatchIndexer indexer =
                        new ResourceChunkBatchIndexer(metadata, missing, catalog);
                reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        session,
                        indexer.positions(),
                        indexer.wantedBlockIds(),
                        diagnostics,
                        indexer::accept,
                        progress
                );
                List<ResourceChunkIndexEntry> publish = indexer.finish();
                store.publish(publish);
                counters.published = Math.addExact(
                        counters.published,
                        publish.size()
                );
                for (ResourceChunkIndexEntry entry : publish) {
                    counters.occurrenceColumns = Math.addExact(
                            counters.occurrenceColumns,
                            entry.occurrences().size()
                    );
                }
            }

            progress.progress(
                    "Indexing resource snapshot",
                    batchIndex + 1,
                    batches.size()
            );
        }

        boolean complete = coverageComplete(store, metadata, observed);
        if (complete) {
            if (markerInvalidated) {
                store.markScanComplete();
            }
        } else if (!markerInvalidated) {
            store.markScanIncomplete();
        }
        progress.done("Resource snapshot indexing complete");
        return new Result(
                complete,
                blocksCatalogued,
                counters.hits,
                counters.published,
                counters.occurrenceColumns
        );
    }

    private boolean coverageComplete(
            ResourceIndexStore store,
            WorldMetadata metadata,
            List<MapChunkCoordinate> observed
    ) {
        List<List<MapChunkCoordinate>> batches = batchPlanner.plan(observed);
        for (List<MapChunkCoordinate> batch : batches) {
            List<ChunkPosition> positions = chunkPositions(metadata, batch);
            Map<ChunkPosition, ResourceChunkIndexLookup> lookups =
                    store.readCoverage(positions);
            for (ChunkPosition position : positions) {
                if (lookups.getOrDefault(
                        position,
                        ResourceChunkIndexLookup.miss()
                ).status() != ResourceChunkIndexLookup.Status.HIT) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<ChunkPosition> chunkPositions(
            WorldMetadata metadata,
            List<MapChunkCoordinate> coordinates
    ) {
        int verticalChunkCount = Math.toIntExact(
                Math.floorDiv(
                        Math.addExact(
                                metadata.mapSizeY(),
                                ChunkCoordinate.SIZE_BLOCKS - 1L
                        ),
                        ChunkCoordinate.SIZE_BLOCKS
                )
        );
        if (verticalChunkCount <= 0) {
            return List.of();
        }

        List<ChunkPosition> result = new ArrayList<>(
                Math.multiplyExact(coordinates.size(), verticalChunkCount)
        );
        for (MapChunkCoordinate coordinate : coordinates) {
            for (int chunkY = 0; chunkY < verticalChunkCount; chunkY++) {
                result.add(new ChunkPosition(
                        coordinate.x(),
                        chunkY,
                        coordinate.z(),
                        0
                ));
            }
        }
        return List.copyOf(result);
    }

    record Result(
            boolean coverageComplete,
            int blocksCatalogued,
            int hits,
            int published,
            long occurrenceColumnsPublished
    ) {
    }

    private static final class Counters {
        private int hits;
        private int published;
        private long occurrenceColumns;
    }
}
