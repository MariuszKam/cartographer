package cartographer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedChunkTest {

    @Test
    void availableEmptyLiquidLayerIsDistinctFromUnavailable() {
        ParsedChunk empty = chunk(new int[]{7}, new int[]{0}, true, "");
        ParsedChunk unavailable = chunk(new int[]{7}, null, false, "not decoded");

        assertTrue(empty.liquidLayerAvailable());
        assertEquals(0, empty.liquidIdAt(0, 0, 0));
        assertEquals(1, empty.liquidIds().length);

        assertThrows(
                IllegalStateException.class,
                () -> unavailable.liquidIdAt(0, 0, 0)
        );
        assertThrows(
                IllegalStateException.class,
                unavailable::liquidIds
        );
    }

    @Test
    void constructorRequiresConsistentLiquidState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chunk(new int[]{7}, null, true, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunk(new int[]{7}, new int[]{0}, false, "not decoded")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunk(new int[]{7}, null, false, " ")
        );

        ParsedChunk unavailable =
                chunk(new int[]{7}, null, false, "not decoded");
        assertEquals("not decoded", unavailable.liquidDecodeError());

        ParsedChunk available =
                chunk(new int[]{7}, new int[]{3}, true, "");
        assertTrue(available.liquidLayerAvailable());
    }

    @Test
    void availableLayersRemainDefensivelyCopied() {
        int[] blocks = {7};
        int[] liquids = {3};
        ParsedChunk chunk = chunk(blocks, liquids, true, "");

        blocks[0] = 8;
        liquids[0] = 4;
        assertEquals(7, chunk.blockIdAt(0, 0, 0));
        assertEquals(3, chunk.liquidIdAt(0, 0, 0));

        int[] returnedBlocks = chunk.blockIds();
        int[] returnedLiquids = chunk.liquidIds();
        returnedBlocks[0] = 9;
        returnedLiquids[0] = 5;
        assertArrayEquals(new int[]{7}, chunk.blockIds());
        assertArrayEquals(new int[]{3}, chunk.liquidIds());
    }

    @Test
    void decodedLayerConstructionPreservesValuesAndValidatesLengths() {
        DecodedChunkLayer blocks =
                DecodedChunkLayer.builder(1)
                        .set(0, 7)
                        .build();
        DecodedChunkLayer liquids =
                DecodedChunkLayer.builder(1)
                        .set(0, 3)
                        .build();

        ParsedChunk chunk = ParsedChunk.fromDecodedLayers(
                new ChunkCoordinate(0, 0, 0),
                0,
                1,
                1,
                1,
                blocks,
                liquids,
                2,
                true,
                ""
        );

        assertEquals(7, chunk.blockIdAt(0, 0, 0));
        assertEquals(3, chunk.liquidIdAt(0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ParsedChunk.fromDecodedLayers(
                        new ChunkCoordinate(0, 0, 0),
                        0,
                        2,
                        1,
                        1,
                        blocks,
                        liquids,
                        2,
                        true,
                        ""
                )
        );

        DecodedChunkLayer validBlocks =
                DecodedChunkLayer.builder(2).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> ParsedChunk.fromDecodedLayers(
                        new ChunkCoordinate(0, 0, 0),
                        0,
                        2,
                        1,
                        1,
                        validBlocks,
                        liquids,
                        2,
                        true,
                        ""
                )
        );
    }

    private ParsedChunk chunk(
            int[] blocks,
            int[] liquids,
            boolean available,
            String error
    ) {
        return new ParsedChunk(
                new ChunkCoordinate(0, 0, 0),
                0,
                1,
                1,
                1,
                blocks,
                liquids,
                2,
                available,
                error
        );
    }
}
