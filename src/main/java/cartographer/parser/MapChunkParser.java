package cartographer.parser;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapTile;
import cartographer.model.ParseResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MapChunkParser {
    private static final byte[] MAGIC = "VSCMAP1".getBytes(StandardCharsets.US_ASCII);

    public ParseResult<MapChunk> parse(MapChunkCoordinate coordinate, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return ParseResult.failure("mapchunk payload is empty");
        }
        if (!hasMagic(payload, MAGIC)) {
            return ParseResult.failure("unsupported mapchunk payload format");
        }
        if (payload.length < MAGIC.length + Integer.BYTES) {
            return ParseResult.failure("mapchunk fixture payload is truncated");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(MAGIC.length);
        int count = buffer.getInt();
        if (count < 0 || count > 1_048_576) {
            return ParseResult.failure("mapchunk tile count is invalid: " + count);
        }
        if (buffer.remaining() < count * 16) {
            return ParseResult.failure("mapchunk fixture payload does not contain all tile records");
        }

        List<MapTile> tiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int worldX = buffer.getInt();
            int worldZ = buffer.getInt();
            int height = buffer.getInt();
            int argb = buffer.getInt();
            tiles.add(new MapTile(worldX, worldZ, height, argb));
        }
        return ParseResult.success(new MapChunk(coordinate, tiles));
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
