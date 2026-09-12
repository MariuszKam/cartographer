package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActualBlockMapScannerTest {

    @Test
    void aggregatesMultipleMatchingBlocksIntoSingleXZCell() {
        ParsedChunk chunk =
                chunkWithCopperColumn();

        ActualBlockMap map =
                new ActualBlockMapScanner().scan(
                        List.of(
                                chunk
                        ),
                        Map.of(
                                0,
                                new BlockInfo(
                                        0,
                                        "air"
                                ),
                                1,
                                new BlockInfo(
                                        1,
                                        "ore-poor-nativecopper-granite"
                                ),
                                2,
                                new BlockInfo(
                                        2,
                                        "rock-granite"
                                )
                        ),
                        0,
                        0,
                        16,
                        "copper"
                );

        assertEquals(
                3,
                map.matchingBlocks()
        );

        assertEquals(
                1,
                map.hitColumns()
        );

        ActualBlockMapCell cell =
                map.cells()
                        .getFirst();

        assertEquals(
                0,
                cell.worldX()
        );

        assertEquals(
                0,
                cell.worldZ()
        );

        assertEquals(
                3,
                cell.matchCount()
        );

        assertEquals(
                4,
                cell.minY()
        );

        assertEquals(
                6,
                cell.maxY()
        );
    }

    @Test
    void filtersEachBlockByExactHorizontalRadius() {
        ParsedChunk chunk =
                chunkWithCopperInsideAndOutsideRadius();

        ActualBlockMap map =
                new ActualBlockMapScanner().scan(
                        List.of(
                                chunk
                        ),
                        Map.of(
                                1,
                                new BlockInfo(
                                        1,
                                        "ore-poor-nativecopper-granite"
                                ),
                                2,
                                new BlockInfo(
                                        2,
                                        "rock-granite"
                                )
                        ),
                        0,
                        0,
                        4,
                        "copper"
                );

        assertEquals(
                1,
                map.matchingBlocks()
        );

        assertEquals(
                1,
                map.hitColumns()
        );

        ActualBlockMapCell cell =
                map.cells()
                        .getFirst();

        assertEquals(
                3,
                cell.worldX()
        );

        assertEquals(
                0,
                cell.worldZ()
        );
    }

    private ParsedChunk chunkWithCopperColumn() {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        int[] blocks =
                new int[
                        size
                                * size
                                * size
                        ];

        Arrays.fill(
                blocks,
                2
        );

        blocks[
                index(
                        0,
                        4,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        0,
                        5,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        0,
                        6,
                        0
                )
                ] =
                1;

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        0,
                        0
                ),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private ParsedChunk chunkWithCopperInsideAndOutsideRadius() {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        int[] blocks =
                new int[
                        size
                                * size
                                * size
                        ];

        Arrays.fill(
                blocks,
                2
        );

        blocks[
                index(
                        3,
                        5,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        5,
                        5,
                        0
                )
                ] =
                1;

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        0,
                        0
                ),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private int index(
            int x,
            int y,
            int z
    ) {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        return (y * size + z)
                * size
                + x;
    }
}
