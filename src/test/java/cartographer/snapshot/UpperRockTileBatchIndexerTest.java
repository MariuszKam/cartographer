package cartographer.snapshot;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockColumnState;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.SelectiveChunkVisit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpperRockTileBatchIndexerTest {

    @Test
    void preservesUpperRockCoverageSemanticsAcrossTiles() {
        WorldMetadata metadata = new WorldMetadata(96, 64, 32);
        RockCatalog catalog = RockCatalog.from(Map.of(
                0, new BlockInfo(0, "game:air"),
                7, new BlockInfo(7, "game:rock-granite")
        ));
        UpperRockTileBatchIndexer indexer =
                new UpperRockTileBatchIndexer(
                        metadata,
                        List.of(
                                new MapChunkCoordinate(0, 0),
                                new MapChunkCoordinate(1, 0),
                                new MapChunkCoordinate(2, 0)
                        ),
                        catalog
                );

        // Completion order is intentionally mixed.
        indexer.accept(SelectiveChunkVisit.paletteRejected(
                new cartographer.model.ChunkPosition(0, 1, 0, 0)
        ));
        indexer.accept(SelectiveChunkVisit.decoded(
                new cartographer.model.ChunkPosition(0, 0, 0, 0),
                chunkWithRock(0, 0, 0, 10, 7)
        ));

        indexer.accept(SelectiveChunkVisit.missing(
                new cartographer.model.ChunkPosition(1, 1, 0, 0)
        ));
        indexer.accept(SelectiveChunkVisit.decoded(
                new cartographer.model.ChunkPosition(1, 0, 0, 0),
                chunkWithRock(1, 0, 0, 10, 7)
        ));

        indexer.accept(SelectiveChunkVisit.paletteRejected(
                new cartographer.model.ChunkPosition(2, 0, 0, 0)
        ));
        indexer.accept(SelectiveChunkVisit.paletteRejected(
                new cartographer.model.ChunkPosition(2, 1, 0, 0)
        ));

        List<UpperRockTile> tiles = indexer.finish();

        assertEquals(3, tiles.size());
        assertEquals(
                RockColumnState.OBSERVED,
                tiles.get(0).stateAt(0, 0)
        );
        assertEquals(7, tiles.get(0).blockIdAt(0, 0));
        assertEquals(10, tiles.get(0).rockYAt(0, 0));
        assertEquals(
                RockColumnState.NO_ROCK,
                tiles.get(0).stateAt(1, 0)
        );

        assertEquals(
                RockColumnState.UNAVAILABLE,
                tiles.get(1).stateAt(0, 0),
                "missing coverage above a candidate must make it unavailable"
        );

        assertEquals(
                RockColumnState.NO_ROCK,
                tiles.get(2).stateAt(0, 0),
                "fully available palette-rejected coverage proves no rock"
        );
    }

    @Test
    void missingCoverageBelowHighestCandidateDoesNotInvalidateObservation() {
        WorldMetadata metadata = new WorldMetadata(32, 64, 32);
        RockCatalog catalog = RockCatalog.from(Map.of(
                0, new BlockInfo(0, "game:air"),
                7, new BlockInfo(7, "game:rock-granite")
        ));
        UpperRockTileBatchIndexer indexer =
                new UpperRockTileBatchIndexer(
                        metadata,
                        List.of(new MapChunkCoordinate(0, 0)),
                        catalog
                );

        indexer.accept(SelectiveChunkVisit.missing(
                new cartographer.model.ChunkPosition(0, 0, 0, 0)
        ));
        indexer.accept(SelectiveChunkVisit.decoded(
                new cartographer.model.ChunkPosition(0, 1, 0, 0),
                chunkWithRock(0, 1, 0, 18, 7)
        ));

        UpperRockTile tile = indexer.finish().getFirst();

        assertEquals(RockColumnState.OBSERVED, tile.stateAt(0, 0));
        assertEquals(50, tile.rockYAt(0, 0));
    }

    private static ParsedChunk chunkWithRock(
            int chunkX,
            int chunkY,
            int chunkZ,
            int localY,
            int blockId
    ) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int index = (localY * size) * size;
        blocks[index] = blockId;
        return cartographer.model.ParsedChunkFixtures.create(
                new ChunkCoordinate(chunkX, chunkY, chunkZ),
                chunkY * size,
                size,
                size,
                size,
                blocks
        );
    }
}
