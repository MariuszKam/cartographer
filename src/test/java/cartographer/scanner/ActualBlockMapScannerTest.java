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

    @Test
    void filtersByClosedYRangeBeforeAggregatingCells() {
        ActualBlockMap map =
                new ActualBlockMapScanner().scan(
                        chunksWithCopperAtYValues(),
                        registry(),
                        0,
                        0,
                        16,
                        "copper",
                        new ActualBlockYFilter(
                                16,
                                63
                        )
                );

        assertEquals(
                2,
                map.matchingBlocks()
        );

        assertEquals(
                1,
                map.hitColumns()
        );

        assertEquals(
                20,
                map.minMatchedY()
        );

        assertEquals(
                40,
                map.maxMatchedY()
        );

        ActualBlockMapCell cell =
                map.cells()
                        .getFirst();

        assertEquals(
                2,
                cell.matchCount()
        );

        assertEquals(
                20,
                cell.minY()
        );

        assertEquals(
                40,
                cell.maxY()
        );
    }

    @Test
    void filtersByOnlyMinimumY() {
        ActualBlockMap map =
                new ActualBlockMapScanner().scan(
                        chunksWithCopperAtYValues(),
                        registry(),
                        0,
                        0,
                        16,
                        "copper",
                        new ActualBlockYFilter(
                                32,
                                null
                        )
                );

        assertEquals(
                2,
                map.matchingBlocks()
        );

        ActualBlockMapCell cell =
                map.cells()
                        .getFirst();

        assertEquals(
                2,
                cell.matchCount()
        );

        assertEquals(
                40,
                cell.minY()
        );

        assertEquals(
                70,
                cell.maxY()
        );
    }

    @Test
    void filtersByOnlyMaximumY() {
        ActualBlockMap map =
                new ActualBlockMapScanner().scan(
                        chunksWithCopperAtYValues(),
                        registry(),
                        0,
                        0,
                        16,
                        "copper",
                        new ActualBlockYFilter(
                                null,
                                31
                        )
                );

        assertEquals(
                2,
                map.matchingBlocks()
        );

        ActualBlockMapCell cell =
                map.cells()
                        .getFirst();

        assertEquals(
                2,
                cell.matchCount()
        );

        assertEquals(
                5,
                cell.minY()
        );

        assertEquals(
                20,
                cell.maxY()
        );
    }

    private Map<Integer, BlockInfo> registry() {
        return Map.of(
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
        );
    }

    private List<ParsedChunk> chunksWithCopperAtYValues() {
        return List.of(
                chunkWithCopperAtLocalY(
                        0,
                        5,
                        20
                ),
                chunkWithCopperAtLocalY(
                        1,
                        8
                ),
                chunkWithCopperAtLocalY(
                        2,
                        6
                )
        );
    }

    private ParsedChunk chunkWithCopperAtLocalY(
            int chunkY,
            int... localYs
    ) {
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

        for (int localY : localYs) {
            blocks[
                    index(
                            0,
                            localY,
                            0
                    )
                    ] =
                    1;
        }

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        chunkY,
                        0
                ),
                chunkY
                        * size,
                size,
                size,
                size,
                blocks
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
