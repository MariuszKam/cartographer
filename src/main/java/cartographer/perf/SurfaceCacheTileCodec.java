package cartographer.perf;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClassCode;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Deterministic binary format for one full-mapchunk Surface artifact. */
public final class SurfaceCacheTileCodec {
    private static final int MAGIC = 0x53435431;
    private static final int VERSION = 1;
    private static final int PROFILE_VERSION = 1; // surface-render-v1: no foliage, liquid required
    private static final int HEADER_BYTES = 12 * Integer.BYTES;

    private SurfaceCacheTileCodec() {
    }

    public static byte[] encode(SurfaceCacheTile tile) {
        Objects.requireNonNull(tile, "tile is required");
        int cells = tile.cellCount();
        int payloadBytes = Math.multiplyExact(cells, 14);
        ByteBuffer buffer = ByteBuffer.allocate(Math.addExact(HEADER_BYTES, payloadBytes))
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC).putInt(VERSION).putInt(PROFILE_VERSION)
                .putInt(tile.coordinate().x()).putInt(tile.coordinate().z())
                .putInt(tile.width()).putInt(tile.height()).putInt(tile.sourceMode().code())
                .putInt(tile.diagnosticColumnsScanned())
                .putInt(tile.diagnosticEmptyColumns())
                .putInt(tile.diagnosticLiquidUnavailableColumns())
                .putInt(cells);
        byte[] state = tile.state();
        int[] surfaceY = tile.surfaceY();
        int[] blockIds = tile.blockIds();
        int[] liquidIds = tile.liquidBlockIds();
        byte[] classes = tile.surfaceClassCodes();
        for (int index = 0; index < cells; index++) {
            buffer.put(state[index]).putInt(surfaceY[index]).putInt(blockIds[index])
                    .putInt(liquidIds[index]).put(classes[index]);
        }
        return buffer.array();
    }

    public static SurfaceCacheTile decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded tile is required");
        if (encoded.length < HEADER_BYTES) throw new IllegalArgumentException("Surface tile payload is truncated");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            if (buffer.getInt() != MAGIC) throw new IllegalArgumentException("wrong Surface tile magic");
            if (buffer.getInt() != VERSION) throw new IllegalArgumentException("unsupported Surface tile version");
            if (buffer.getInt() != PROFILE_VERSION) throw new IllegalArgumentException("unsupported Surface profile");
            int x = buffer.getInt();
            int z = buffer.getInt();
            int width = buffer.getInt();
            int height = buffer.getInt();
            SurfaceCacheTile.SourceMode mode = SurfaceCacheTile.SourceMode.decode(buffer.getInt());
            int scanned = buffer.getInt();
            int empty = buffer.getInt();
            int unavailable = buffer.getInt();
            int count = buffer.getInt();
            int expected = Math.multiplyExact(width, height);
            if (count != expected) throw new IllegalArgumentException("invalid Surface cell count");
            if (buffer.remaining() != Math.multiplyExact(count, 14)) {
                throw new IllegalArgumentException("Surface tile payload has trailing or missing bytes");
            }
            byte[] state = new byte[count];
            int[] surfaceY = new int[count];
            int[] blockIds = new int[count];
            int[] liquidIds = new int[count];
            byte[] classes = new byte[count];
            for (int index = 0; index < count; index++) {
                state[index] = buffer.get();
                surfaceY[index] = buffer.getInt();
                blockIds[index] = buffer.getInt();
                liquidIds[index] = buffer.getInt();
                classes[index] = buffer.get();
                SurfaceClassCode.decode(classes[index]);
            }
            return new SurfaceCacheTile(new MapChunkCoordinate(x, z), width, height, state,
                    surfaceY, blockIds, liquidIds, classes, mode, scanned, empty, unavailable);
        } catch (BufferUnderflowException | ArithmeticException exception) {
            throw new IllegalArgumentException("Surface tile payload is invalid or truncated", exception);
        }
    }
}
