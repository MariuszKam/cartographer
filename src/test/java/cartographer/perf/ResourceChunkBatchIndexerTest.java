package cartographer.perf;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.save.SelectiveChunkVisit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceChunkBatchIndexerTest {

    @Test
    void compactsExactOreOccurrencesAndPreservesCoverageStates() {
        ResourceBlockCatalog catalog = ResourceBlockCatalog.from(Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:ore-nativecopper-granite"),
                3, new BlockInfo(3, "game:ore-cassiterite-granite")
        ));

        ChunkPosition decodedPosition = new ChunkPosition(4, 1, 7, 0);
        ChunkPosition rejectedPosition = new ChunkPosition(5, 1, 7, 0);
        ChunkPosition missingPosition = new ChunkPosition(6, 1, 7, 0);
        ChunkPosition failedPosition = new ChunkPosition(7, 1, 7, 0);

        ResourceChunkBatchIndexer indexer =
                new ResourceChunkBatchIndexer(
                        new cartographer.model.WorldMetadata(
                                256,
                                64,
                                256
                        ),
                        List.of(
                                decodedPosition,
                                rejectedPosition,
                                missingPosition,
                                failedPosition
                        ),
                        catalog
                );

        indexer.accept(
                SelectiveChunkVisit.decoded(
                        decodedPosition,
                        chunkWithOre(decodedPosition)
                )
        );
        indexer.accept(
                SelectiveChunkVisit.paletteRejected(rejectedPosition)
        );
        indexer.accept(
                SelectiveChunkVisit.missing(missingPosition)
        );
        indexer.accept(
                SelectiveChunkVisit.failed(
                        failedPosition,
                        "decode failed"
                )
        );

        Map<ChunkPosition, ResourceChunkIndexEntry> entries =
                indexer.finish().stream().collect(
                        java.util.stream.Collectors.toMap(
                                ResourceChunkIndexEntry::position,
                                entry -> entry
                        )
                );

        ResourceChunkIndexEntry decoded = entries.get(decodedPosition);
        assertEquals(
                ResourceChunkCoverageStatus.AVAILABLE,
                decoded.coverageStatus()
        );
        assertEquals(List.of(2, 3), decoded.blockIdsPresent());
        assertEquals(2, decoded.occurrences().size());

        ResourceOccurrence copper = decoded.occurrences().stream()
                .filter(occurrence -> occurrence.blockId() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(2, copper.localX());
        assertEquals(3, copper.localZ());
        assertEquals((1L << 1) | (1L << 4), copper.localYMask());
        assertEquals(2, copper.count());
        assertEquals(1, copper.minLocalY());
        assertEquals(4, copper.maxLocalY());

        ResourceOccurrence tin = decoded.occurrences().stream()
                .filter(occurrence -> occurrence.blockId() == 3)
                .findFirst()
                .orElseThrow();
        assertEquals(5, tin.localX());
        assertEquals(6, tin.localZ());
        assertEquals(1L << 31, tin.localYMask());
        assertEquals(31, tin.maxLocalY());

        assertEquals(
                ResourceChunkCoverageStatus.AVAILABLE,
                entries.get(rejectedPosition).coverageStatus()
        );
        assertEquals(
                List.of(),
                entries.get(rejectedPosition).occurrences()
        );
        assertEquals(
                ResourceChunkCoverageStatus.MISSING,
                entries.get(missingPosition).coverageStatus()
        );
        assertEquals(
                ResourceChunkCoverageStatus.FAILED,
                entries.get(failedPosition).coverageStatus()
        );
    }

    @Test
    void ignoresDecodedOreOutsidePartialWorldEdge() {
        ResourceBlockCatalog catalog = ResourceBlockCatalog.from(Map.of(
                2, new BlockInfo(2, "game:ore-nativecopper-granite")
        ));
        ChunkPosition position = new ChunkPosition(1, 1, 1, 0);
        ResourceChunkBatchIndexer indexer =
                new ResourceChunkBatchIndexer(
                        new cartographer.model.WorldMetadata(
                                33,
                                33,
                                33
                        ),
                        List.of(position),
                        catalog
                );

        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int[] liquids = new int[blocks.length];
        Arrays.fill(blocks, 1);
        set(blocks, size, 0, 0, 0, 2);
        set(blocks, size, 1, 1, 1, 2);

        indexer.accept(
                SelectiveChunkVisit.decoded(
                        position,
                        new ParsedChunk(
                                new ChunkCoordinate(1, 1, 1),
                                32,
                                size,
                                size,
                                size,
                                blocks,
                                liquids,
                                2
                        )
                )
        );

        ResourceChunkIndexEntry entry = indexer.finish().getFirst();
        assertEquals(1, entry.occurrences().size());
        ResourceOccurrence occurrence = entry.occurrences().getFirst();
        assertEquals(0, occurrence.localX());
        assertEquals(0, occurrence.localZ());
        assertEquals(1L, occurrence.localYMask());
    }

    private ParsedChunk chunkWithOre(ChunkPosition position) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int cells = size * size * size;
        int[] blocks = new int[cells];
        int[] liquids = new int[cells];
        Arrays.fill(blocks, 1);

        set(blocks, size, 2, 1, 3, 2);
        set(blocks, size, 2, 4, 3, 2);
        set(blocks, size, 5, 31, 6, 3);

        return new ParsedChunk(
                new ChunkCoordinate(
                        position.x(),
                        position.y(),
                        position.z()
                ),
                position.y() * size,
                size,
                size,
                size,
                blocks,
                liquids,
                2
        );
    }

    private void set(
            int[] blocks,
            int size,
            int x,
            int y,
            int z,
            int blockId
    ) {
        blocks[(y * size + z) * size + x] = blockId;
    }
}
