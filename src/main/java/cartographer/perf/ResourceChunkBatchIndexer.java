package cartographer.perf;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Bounded PF-2.5 builder for resource membership/occurrence state.
 *
 * <p>The selective reader already performs the authoritative palette check.
 * A palette rejection therefore means source coverage is available and no
 * indexed ore block occurs in that chunk. Decoded chunks are scanned once and
 * compacted into one 32-bit local-Y mask per block/local-X/local-Z column.</p>
 */
public final class ResourceChunkBatchIndexer {
    private final ResourceBlockCatalog catalog;
    private final List<ChunkPosition> positions;
    private final Map<ChunkPosition, ResourceChunkIndexEntry> entries;
    private boolean finished;

    public ResourceChunkBatchIndexer(
            Collection<ChunkPosition> positions,
            ResourceBlockCatalog catalog
    ) {
        Objects.requireNonNull(positions, "positions are required");
        this.catalog = Objects.requireNonNull(catalog, "catalog is required");

        LinkedHashSet<ChunkPosition> unique = new LinkedHashSet<>();
        for (ChunkPosition position : positions) {
            Objects.requireNonNull(
                    position,
                    "positions cannot contain null"
            );
            if (position.dimension() != 0) {
                throw new IllegalArgumentException(
                        "resource indexing only supports main-world dimension 0"
                );
            }
            unique.add(position);
        }
        this.positions = unique.stream()
                .sorted(
                        Comparator.comparingInt(ChunkPosition::y)
                                .thenComparingInt(ChunkPosition::z)
                                .thenComparingInt(ChunkPosition::x)
                )
                .toList();
        this.entries = new LinkedHashMap<>();
        this.positions.forEach(position -> entries.put(position, null));
    }

    public List<ChunkPosition> positions() {
        ensureOpen();
        return positions;
    }

    public int[] wantedBlockIds() {
        return catalog.blockIds();
    }

    public void accept(SelectiveChunkVisit visit) {
        ensureOpen();
        Objects.requireNonNull(visit, "visit is required");
        ChunkPosition position = visit.position();
        if (!entries.containsKey(position)) {
            throw new IllegalArgumentException(
                    "resource visit is outside the indexed batch"
            );
        }
        if (entries.get(position) != null) {
            throw new IllegalArgumentException(
                    "duplicate terminal resource visit"
            );
        }

        ResourceChunkIndexEntry entry = switch (visit.status()) {
            case PALETTE_REJECTED ->
                    ResourceChunkIndexEntry.available(
                            position,
                            List.of()
                    );
            case MISSING -> ResourceChunkIndexEntry.missing(position);
            case FAILED -> ResourceChunkIndexEntry.failed(position);
            case DECODED -> indexDecoded(position, visit.chunk());
        };
        entries.put(position, entry);
    }

    public List<ResourceChunkIndexEntry> finish() {
        ensureOpen();
        finished = true;
        List<ResourceChunkIndexEntry> result =
                new ArrayList<>(positions.size());
        for (ChunkPosition position : positions) {
            ResourceChunkIndexEntry entry = entries.get(position);
            if (entry == null) {
                throw new IllegalStateException(
                        "resource indexing did not receive a terminal visit for "
                                + position
                );
            }
            result.add(entry);
        }
        return List.copyOf(result);
    }

    private ResourceChunkIndexEntry indexDecoded(
            ChunkPosition position,
            ParsedChunk chunk
    ) {
        Objects.requireNonNull(chunk, "decoded visit requires a chunk");
        ChunkCoordinate expected = new ChunkCoordinate(
                position.x(),
                position.y(),
                position.z()
        );
        if (!expected.equals(chunk.coordinate())) {
            throw new IllegalArgumentException(
                    "decoded resource chunk coordinate does not match visit"
            );
        }
        if (chunk.sizeX() > 32
                || chunk.sizeY() > 32
                || chunk.sizeZ() > 32) {
            throw new IllegalArgumentException(
                    "resource occurrence masks require chunk dimensions <= 32"
            );
        }

        int columns = Math.multiplyExact(chunk.sizeX(), chunk.sizeZ());
        Map<Integer, long[]> masksByBlockId = new TreeMap<>();

        for (int localY = 0; localY < chunk.sizeY(); localY++) {
            long yBit = 1L << localY;
            for (int localZ = 0; localZ < chunk.sizeZ(); localZ++) {
                int row = localZ * chunk.sizeX();
                for (int localX = 0; localX < chunk.sizeX(); localX++) {
                    int blockId = chunk.blockIdAt(
                            localX,
                            localY,
                            localZ
                    );
                    if (!catalog.contains(blockId)) {
                        continue;
                    }
                    long[] masks = masksByBlockId.computeIfAbsent(
                            blockId,
                            ignored -> new long[columns]
                    );
                    masks[row + localX] |= yBit;
                }
            }
        }

        List<ResourceOccurrence> occurrences = new ArrayList<>();
        for (Map.Entry<Integer, long[]> block :
                masksByBlockId.entrySet()) {
            long[] masks = block.getValue();
            for (int localZ = 0; localZ < chunk.sizeZ(); localZ++) {
                int row = localZ * chunk.sizeX();
                for (int localX = 0; localX < chunk.sizeX(); localX++) {
                    long mask = masks[row + localX];
                    if (mask == 0L) {
                        continue;
                    }
                    occurrences.add(
                            new ResourceOccurrence(
                                    position,
                                    block.getKey(),
                                    localX,
                                    localZ,
                                    mask
                            )
                    );
                }
            }
        }

        return ResourceChunkIndexEntry.available(
                position,
                occurrences
        );
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException(
                    "resource batch indexer is already finished"
            );
        }
    }
}
