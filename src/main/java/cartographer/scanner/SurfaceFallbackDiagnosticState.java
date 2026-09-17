package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Primitive fallback-column diagnostics. The state is intentionally separate
 * from the clipped SurfaceMap: legacy fallback scanning counts every column
 * in a delivered server chunk, including columns outside the render circle.
 */
final class SurfaceFallbackDiagnosticState {
    private static final byte CONSIDERED = 1;
    private static final byte RESOLVED = 1 << 1;
    private static final byte LIQUID_UNAVAILABLE = 1 << 2;

    private final Map<ChunkPosition, byte[]> chunks = new HashMap<>();

    void considerChunk(ParsedChunk chunk) {
        ChunkPosition key = horizontalKey(chunk);
        byte[] columns = chunks.computeIfAbsent(
                key,
                ignored -> new byte[ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS]
        );
        for (int localZ = 0; localZ < Math.min(chunk.sizeZ(), ChunkCoordinate.SIZE_BLOCKS); localZ++) {
            for (int localX = 0; localX < Math.min(chunk.sizeX(), ChunkCoordinate.SIZE_BLOCKS); localX++) {
                int index = localZ * ChunkCoordinate.SIZE_BLOCKS + localX;
                columns[index] |= CONSIDERED;
                if (!chunk.liquidLayerAvailable()) {
                    columns[index] |= LIQUID_UNAVAILABLE;
                }
            }
        }
    }

    void markResolved(ParsedChunk chunk, int localX, int localZ) {
        byte[] columns = chunks.get(horizontalKey(chunk));
        if (columns == null
                || localX < 0 || localX >= ChunkCoordinate.SIZE_BLOCKS
                || localZ < 0 || localZ >= ChunkCoordinate.SIZE_BLOCKS) {
            return;
        }
        columns[localZ * ChunkCoordinate.SIZE_BLOCKS + localX] |= RESOLVED;
    }

    Summary summary() {
        int considered = 0;
        int resolved = 0;
        int liquidUnavailable = 0;
        for (byte[] columns : chunks.values()) {
            for (byte state : columns) {
                if ((state & CONSIDERED) != 0) {
                    considered = Math.addExact(considered, 1);
                }
                if ((state & RESOLVED) != 0) {
                    resolved = Math.addExact(resolved, 1);
                }
                if ((state & LIQUID_UNAVAILABLE) != 0) {
                    liquidUnavailable = Math.addExact(liquidUnavailable, 1);
                }
            }
        }
        return new Summary(considered, resolved,
                Math.subtractExact(considered, resolved), liquidUnavailable);
    }

    private static ChunkPosition horizontalKey(ParsedChunk chunk) {
        return new ChunkPosition(
                chunk.coordinate().x(), 0, chunk.coordinate().z(), 0);
    }

    record Summary(
            int consideredColumns,
            int resolvedColumns,
            int emptyColumns,
            int liquidUnavailableColumns
    ) {
    }
}
