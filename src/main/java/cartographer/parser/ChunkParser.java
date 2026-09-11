package cartographer.parser;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ParsedChunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class ChunkParser {
    private static final byte[] MAGIC = "VSCCHUNK1".getBytes(StandardCharsets.US_ASCII);

    public ParseResult<ParsedChunk> parse(ChunkCoordinate coordinate, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return ParseResult.failure("chunk payload is empty");
        }
        if (!hasMagic(payload, MAGIC)) {
            return ParseResult.failure("unsupported chunk payload format");
        }
        if (payload.length < MAGIC.length + 16) {
            return ParseResult.failure("chunk fixture payload is truncated");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(MAGIC.length);
        int minY = buffer.getInt();
        int sizeX = buffer.getInt();
        int sizeY = buffer.getInt();
        int sizeZ = buffer.getInt();

        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sizeX > 64 || sizeY > 1024 || sizeZ > 64) {
            return ParseResult.failure("chunk dimensions are invalid: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }

        int blockCount = sizeX * sizeY * sizeZ;
        if (buffer.remaining() < blockCount * Integer.BYTES) {
            return ParseResult.failure("chunk fixture payload does not contain all block ids");
        }

        int[] blockIds = new int[blockCount];
        for (int index = 0; index < blockIds.length; index++) {
            blockIds[index] = buffer.getInt();
        }
        return ParseResult.success(new ParsedChunk(coordinate, minY, sizeX, sizeY, sizeZ, blockIds));
    }

    private boolean hasMagic(byte[] payload, byte[] magic) {
        if (payload.length < magic.length) {
            return false;
        }
        for (int index = 0; index < magic.length; index++) {
            if (payload[index] != magic[index]) {
                return false;
            }
        }
        return true;
    }
}
