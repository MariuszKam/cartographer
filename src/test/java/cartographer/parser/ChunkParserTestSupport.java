package cartographer.parser;

import com.github.luben.zstd.Zstd;
import cartographer.model.ChunkCoordinate;
import cartographer.model.DecodedChunkLayer;
import cartographer.model.ParsedChunk;
import cartographer.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class ChunkParserTestSupport {

    protected byte[] emptyLayer() {
        return ByteBuffer.allocate(
                        4
                )
                .order(
                        ByteOrder.LITTLE_ENDIAN
                )
                .putInt(
                        0
                )
                .array();
    }

    protected byte[] encodedLayer(
            int[] palette,
            PaletteIndexAt paletteIndexAt
    ) {
        int bitSize =
                bitSize(
                        palette.length
                );

        ByteBuffer bitPlanes =
                ByteBuffer.allocate(
                                bitSize
                                        * 1024
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        int[][] dataBits =
                new int[bitSize][1024];

        for (int y = 0;
             y < 32;
             y++) {

            for (int z = 0;
                 z < 32;
                 z++) {

                int slice =
                        y * 32
                                + z;

                for (int x = 0;
                     x < 32;
                     x++) {

                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0;
                         bit < bitSize;
                         bit++) {

                        if (((paletteIndex >>> bit)
                                & 1) == 1) {

                            dataBits[bit][slice] |=
                                    1 << x;
                        }
                    }
                }
            }
        }

        for (int bit = 0;
             bit < bitSize;
             bit++) {

            for (int slice = 0;
                 slice < 1024;
                 slice++) {

                bitPlanes.putInt(
                        dataBits[bit][slice]
                );
            }
        }

        byte[] compressed =
                Zstd.compress(
                        bitPlanes.array(),
                        -3
                );

        ByteBuffer out =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + palette.length
                                        * Integer.BYTES
                                        + compressed.length
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        out.putInt(
                -palette.length
                        * Integer.BYTES
        );

        for (int id : palette) {
            out.putInt(
                    id
            );
        }

        out.put(
                compressed
        );

        return out.array();
    }

    protected byte[] compressedPaletteLayer(
            int[] palette,
            PaletteIndexAt paletteIndexAt
    ) {
        int bitSize =
                bitSize(
                        roundedUpPowerOfTwo(
                                palette.length
                        )
                );

        ByteBuffer bitPlanes =
                ByteBuffer.allocate(
                                bitSize
                                        * 1024
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        int[][] dataBits =
                dataBits(
                        bitSize,
                        paletteIndexAt
                );

        for (int bit = 0;
             bit < bitSize;
             bit++) {

            for (int slice = 0;
                 slice < 1024;
                 slice++) {

                bitPlanes.putInt(
                        dataBits[bit][slice]
                );
            }
        }

        ByteBuffer paletteBytes =
                ByteBuffer.allocate(
                                palette.length
                                        * Integer.BYTES
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        for (int id : palette) {
            paletteBytes.putInt(
                    id
            );
        }

        byte[] compressedPalette =
                Zstd.compress(
                        paletteBytes.array(),
                        -3
                );

        byte[] compressedBitPlanes =
                Zstd.compress(
                        bitPlanes.array(),
                        -3
                );

        ByteBuffer out =
                ByteBuffer.allocate(
                                Integer.BYTES
                                        + compressedPalette.length
                                        + compressedBitPlanes.length
                        )
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        out.putInt(
                compressedPalette.length
        );

        out.put(
                compressedPalette
        );

        out.put(
                compressedBitPlanes
        );

        return out.array();
    }

    protected int[][] dataBits(
            int bitSize,
            PaletteIndexAt paletteIndexAt
    ) {
        int[][] dataBits =
                new int[bitSize][1024];

        for (int y = 0;
             y < 32;
             y++) {

            for (int z = 0;
                 z < 32;
                 z++) {

                int slice =
                        y * 32
                                + z;

                for (int x = 0;
                     x < 32;
                     x++) {

                    int index =
                            (y << 10)
                                    | (z << 5)
                                    | x;

                    int paletteIndex =
                            paletteIndexAt.paletteIndexAt(
                                    index
                            );

                    for (int bit = 0;
                         bit < bitSize;
                         bit++) {

                        if (((paletteIndex >>> bit)
                                & 1) == 1) {

                            dataBits[bit][slice] |=
                                    1 << x;
                        }
                    }
                }
            }
        }

        return dataBits;
    }

    protected int bitSize(
            int paletteLength
    ) {
        int bitSize =
                0;

        int value =
                paletteLength - 1;

        while (value > 0) {
            bitSize++;
            value >>>=
                    1;
        }

        return bitSize;
    }

    protected int roundedUpPowerOfTwo(
            int value
    ) {
        if (value <= 1) {
            return value;
        }

        int rounded =
                1;

        while (rounded < value) {
            rounded <<=
                    1;
        }

        return rounded;
    }

    protected byte[] serverChunk(
            byte[] blocks,
            byte[] liquids,
            int compressionVersion
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        if (blocks.length > 0) {
            writeLengthDelimited(
                    out,
                    1,
                    blocks
            );
        }

        writeCompressionVersionField(
                out,
                compressionVersion
        );

        if (liquids.length > 0) {
            writeLengthDelimited(
                    out,
                    16,
                    liquids
            );
        }

        return out.toByteArray();
    }

    protected void writeLengthDelimited(
            ByteArrayOutputStream out,
            int fieldNumber,
            byte[] value
    ) {
        writeVarInt(
                out,
                (fieldNumber << 3)
                        | 2
        );

        writeVarInt(
                out,
                value.length
        );

        out.writeBytes(
                value
        );
    }

    protected void writeCompressionVersionField(
            ByteArrayOutputStream out,
            int value
    ) {
        writeVarInt(
                out,
                15 << 3
        );

        writeVarInt(
                out,
                value
        );
    }

    protected void writeVarInt(
            ByteArrayOutputStream out,
            int value
    ) {
        int remaining =
                value;

        while (remaining >= 0x80) {
            out.write(
                    (remaining & 0x7F)
                            | 0x80
            );

            remaining >>>=
                    7;
        }

        out.write(
                remaining
        );
    }

    protected interface PaletteIndexAt {
        int paletteIndexAt(
                int index
        );
    }

    protected static final class SelectiveRecordingLayerDecoder
            extends ChunkDataLayerDecoder {
        protected int paletteContainsCalls;
        protected int ownedDecodeCalls;
        protected byte[] probedPayload;
        protected byte[] decodedPayload;
        protected int probedOffset;
        protected int decodedOffset;
        protected int probedLength;
        protected int decodedLength;

        @Override
        boolean paletteContainsAny(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                int[] wantedBlockIds,
                ChunkDecodeWorkspace workspace
        ) {
            paletteContainsCalls++;
            probedPayload = payload;
            probedOffset = sourceOffset;
            probedLength = sourceLength;
            return super.paletteContainsAny(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    wantedBlockIds,
                    workspace
            );
        }

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                ChunkDecodeWorkspace workspace
        ) {
            ownedDecodeCalls++;
            decodedPayload = payload;
            decodedOffset = sourceOffset;
            decodedLength = sourceLength;
            return super.decodeOwned(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    workspace
            );
        }
    }

    protected static final class RecordingLayerDecoder
            extends ChunkDataLayerDecoder {
        protected int ownedDecodeCalls;

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int savedCompressionVersion
        ) {
            ownedDecodeCalls++;
            return super.decodeOwned(
                    payload,
                    savedCompressionVersion
            );
        }

        @Override
        DecodedChunkLayer decodeOwned(
                byte[] payload,
                int sourceOffset,
                int sourceLength,
                int savedCompressionVersion,
                ChunkDecodeWorkspace workspace
        ) {
            ownedDecodeCalls++;
            return super.decodeOwned(
                    payload,
                    sourceOffset,
                    sourceLength,
                    savedCompressionVersion,
                    workspace
            );
        }
    }

}
