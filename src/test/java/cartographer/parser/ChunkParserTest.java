package cartographer.parser;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkParserTest {
    @Test
    void parsesFixtureChunkPayload() {
        byte[] payload = fixtureChunk(new int[]{0, 1, 2, 3});

        ParseResult<ParsedChunk> result = new ChunkParser().parse(new ChunkCoordinate(5, 7), payload);

        assertTrue(result.isSuccess());
        ParsedChunk chunk = result.value().orElseThrow();
        assertEquals(2, chunk.sizeX());
        assertEquals(1, chunk.sizeY());
        assertEquals(2, chunk.sizeZ());
        assertEquals(3, chunk.blockIdAt(1, 0, 1));
    }

    static byte[] fixtureChunk(int[] blockIds) {
        byte[] magic = "VSCCHUNK1".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(magic.length + 16 + blockIds.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(magic);
        buffer.putInt(0);
        buffer.putInt(2);
        buffer.putInt(1);
        buffer.putInt(2);
        for (int blockId : blockIds) {
            buffer.putInt(blockId);
        }
        return buffer.array();
    }
}
