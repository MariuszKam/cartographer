package cartographer.parser;

import com.github.luben.zstd.ZstdDecompressCtx;

import java.util.Arrays;

/** Reusable, single-borrower scratch for one chunk decode operation. */
public final class ChunkDecodeWorkspace implements AutoCloseable {
    static final int MAX_PALETTE_BYTES = 1_048_576;
    private byte[] decompressionBuffer;
    private int[] paletteBuffer;
    private final int[] paletteIndexes = new int[ChunkDataLayerDecoder.SIZE];
    private ZstdDecompressCtx decompressor;
    private boolean closed;

    byte[] ensureDecompressionCapacity(int required) {
        ensureOpen();
        if (required < 0) {
            throw new IllegalArgumentException("negative decompression size");
        }
        if (required > MAX_PALETTE_BYTES) {
            throw new IllegalArgumentException("decompressed palette exceeds 1 MiB");
        }
        if (decompressionBuffer == null || decompressionBuffer.length < required) {
            decompressionBuffer = new byte[grow(required, MAX_PALETTE_BYTES)];
        }
        return decompressionBuffer;
    }

    int[] ensurePaletteCapacity(int requiredEntries) {
        ensureOpen();
        if (requiredEntries < 0
                || requiredEntries > MAX_PALETTE_BYTES / Integer.BYTES) {
            throw new IllegalArgumentException("palette exceeds 1 MiB");
        }
        if (paletteBuffer == null || paletteBuffer.length < requiredEntries) {
            paletteBuffer = new int[grow(requiredEntries, MAX_PALETTE_BYTES / Integer.BYTES)];
        }
        return paletteBuffer;
    }

    int[] paletteBuffer() {
        ensureOpen();
        if (paletteBuffer == null) {
            return ensurePaletteCapacity(1);
        }
        return paletteBuffer;
    }

    int[] paletteIndexes() {
        ensureOpen();
        return paletteIndexes;
    }

    long decompress(byte[] destination, int destinationLength,
                    byte[] source, int sourceOffset, int sourceLength) {
        ensureOpen();
        if (decompressor == null) {
            decompressor = new ZstdDecompressCtx();
        }
        return decompressor.decompressByteArray(
                destination, 0, destinationLength,
                source, sourceOffset, sourceLength
        );
    }

    private int grow(int required, int maximum) {
        int capacity = 1;
        while (capacity < required && capacity <= maximum / 2) {
            capacity <<= 1;
        }
        return Math.min(Math.max(capacity, required), maximum);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("chunk decode workspace is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (decompressor != null) {
                decompressor.close();
                decompressor = null;
            }
            decompressionBuffer = null;
            paletteBuffer = null;
            Arrays.fill(paletteIndexes, 0);
        }
    }
}
