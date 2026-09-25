package cartographer.snapshot;

import cartographer.model.MapChunkCoordinate;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Deterministic binary format for one full-mapchunk UPPER_ROCK tile. */
final class UpperRockTileCodec {
    private static final int MAGIC = 0x52543234; // RT24
    private static final int VERSION = 1;
    private static final int PROFILE_VERSION = 1;
    private static final int HEADER_INTS = 11;
    private static final int HEADER_BYTES = HEADER_INTS * Integer.BYTES;
    private static final int CELL_BYTES =
            1 + Integer.BYTES + Integer.BYTES;

    private UpperRockTileCodec() {
    }

    static byte[] encode(UpperRockTile tile) {
        Objects.requireNonNull(tile, "tile is required");
        int cells = tile.cellCount();
        int payloadBytes = Math.multiplyExact(cells, CELL_BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(
                Math.addExact(HEADER_BYTES, payloadBytes)
        ).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(PROFILE_VERSION);
        buffer.putInt(tile.coordinate().x());
        buffer.putInt(tile.coordinate().z());
        buffer.putInt(tile.worldSizeX());
        buffer.putInt(tile.worldSizeY());
        buffer.putInt(tile.worldSizeZ());
        buffer.putInt(tile.width());
        buffer.putInt(tile.height());
        buffer.putInt(cells);

        byte[] states = tile.stateCodesView();
        int[] blockIds = tile.blockIdsView();
        int[] rockY = tile.rockYView();
        for (int index = 0; index < cells; index++) {
            buffer.put(states[index]);
            buffer.putInt(blockIds[index]);
            buffer.putInt(rockY[index]);
        }
        return buffer.array();
    }

    static UpperRockTile decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded tile is required");
        if (encoded.length < HEADER_BYTES) {
            throw new IllegalArgumentException(
                    "ROCK tile payload is truncated"
            );
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded)
                    .order(ByteOrder.BIG_ENDIAN);
            require(buffer.getInt() == MAGIC, "wrong ROCK tile magic");
            require(buffer.getInt() == VERSION, "unsupported ROCK tile version");
            require(
                    buffer.getInt() == PROFILE_VERSION,
                    "unsupported ROCK tile profile"
            );
            MapChunkCoordinate coordinate = new MapChunkCoordinate(
                    buffer.getInt(),
                    buffer.getInt()
            );
            int worldSizeX = buffer.getInt();
            int worldSizeY = buffer.getInt();
            int worldSizeZ = buffer.getInt();
            int width = buffer.getInt();
            int height = buffer.getInt();
            int cells = buffer.getInt();
            require(
                    width > 0 && height > 0
                            && cells == Math.multiplyExact(width, height),
                    "invalid ROCK tile geometry"
            );
            require(
                    buffer.remaining() == Math.multiplyExact(cells, CELL_BYTES),
                    "ROCK tile payload has trailing or missing bytes"
            );

            byte[] states = new byte[cells];
            int[] blockIds = new int[cells];
            int[] rockY = new int[cells];
            for (int index = 0; index < cells; index++) {
                states[index] = buffer.get();
                blockIds[index] = buffer.getInt();
                rockY[index] = buffer.getInt();
            }
            return UpperRockTile.owned(
                    coordinate,
                    worldSizeX,
                    worldSizeY,
                    worldSizeZ,
                    width,
                    height,
                    states,
                    blockIds,
                    rockY
            );
        } catch (BufferUnderflowException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "ROCK tile payload is malformed",
                    exception
            );
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
