package cartographer.cache;

import cartographer.model.MapChunkCoordinate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;
import java.util.Objects;

/** Deterministic binary codec for one compact terrain height tile. */
public final class TerrainHeightTileCodec {
    private static final int MAGIC = 0x54485431;
    private static final int VERSION = 1;
    private static final int RAIN_HEIGHT_FLAG = 1;
    private static final int EFFECTIVE_HEIGHT_FLAG = 2;
    private static final int HEADER_BYTES = Integer.BYTES * 6;

    private TerrainHeightTileCodec() {
    }

    public static byte[] encode(TerrainHeightTile tile) {
        Objects.requireNonNull(tile, "tile is required");
        int[] heights = tile.effectiveHeightsView();
        int flags = (tile.rainHeightAvailable() ? RAIN_HEIGHT_FLAG : 0)
                | (tile.effectiveHeightAvailable() ? EFFECTIVE_HEIGHT_FLAG : 0);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + heights.length * Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC)
                .putInt(VERSION)
                .putInt(tile.coordinate().x())
                .putInt(tile.coordinate().z())
                .putInt(flags)
                .putInt(heights.length);
        for (int height : heights) {
            buffer.putInt(height);
        }
        return buffer.array();
    }

    public static TerrainHeightTile decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded tile is required");
        if (encoded.length < HEADER_BYTES) {
            throw new IllegalArgumentException("terrain tile payload is truncated");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            if (buffer.getInt() != MAGIC) {
                throw new IllegalArgumentException("wrong terrain tile magic");
            }
            if (buffer.getInt() != VERSION) {
                throw new IllegalArgumentException("unsupported terrain tile version");
            }
            int x = buffer.getInt();
            int z = buffer.getInt();
            int flags = buffer.getInt();
            int count = buffer.getInt();
            if ((flags & ~(RAIN_HEIGHT_FLAG | EFFECTIVE_HEIGHT_FLAG)) != 0) {
                throw new IllegalArgumentException("invalid terrain tile flags");
            }
            boolean rainAvailable = (flags & RAIN_HEIGHT_FLAG) != 0;
            boolean effectiveAvailable = (flags & EFFECTIVE_HEIGHT_FLAG) != 0;
            if (rainAvailable && !effectiveAvailable) {
                throw new IllegalArgumentException("rain flag requires effective height flag");
            }
            if (count != 0 && count != TerrainHeightTile.HEIGHT_VALUE_COUNT) {
                throw new IllegalArgumentException("invalid terrain height count");
            }
            if (!effectiveAvailable && count != 0) {
                throw new IllegalArgumentException("unavailable heights must have zero count");
            }
            if (effectiveAvailable && count != TerrainHeightTile.HEIGHT_VALUE_COUNT) {
                throw new IllegalArgumentException("available heights have invalid count");
            }
            if (buffer.remaining() != count * Integer.BYTES) {
                throw new IllegalArgumentException("terrain tile payload has trailing or missing bytes");
            }
            int[] heights = new int[count];
            buffer.asIntBuffer().get(heights);
            return TerrainHeightTile.owned(
                    new MapChunkCoordinate(x, z),
                    rainAvailable,
                    effectiveAvailable,
                    heights
            );
        } catch (BufferUnderflowException exception) {
            throw new IllegalArgumentException("terrain tile payload is truncated", exception);
        }
    }
}
