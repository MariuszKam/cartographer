package cartographer.save;

import cartographer.model.ChunkPosition;

public final class ChunkPosDecoder {

    private static final long COORD_MASK = 0x1FFFFFL;
    private static final long COORD_SIGN_BIT = 0x100000L;
    private static final long COORD_RANGE = 0x200000L;

    private static final long FIVE_BIT_MASK = 0x1FL;
    private static final long Y_MASK = 0x1FFL;

    private static final int X_SHIFT = 0;
    private static final int X_GUARD_SHIFT = 21;
    private static final int DIMENSION_LOW_SHIFT = 22;

    private static final int Z_SHIFT = 27;
    private static final int Z_GUARD_SHIFT = 48;
    private static final int DIMENSION_HIGH_SHIFT = 49;

    private static final int Y_SHIFT = 54;
    private static final int RESERVED_SHIFT = 63;

    private ChunkPosDecoder() {
    }

    public static ChunkPosition decode(
            long packedPosition
    ) {
        validateGuardBits(
                packedPosition
        );

        int x =
                decodeSigned21(
                        packedPosition >>> X_SHIFT
                );

        int z =
                decodeSigned21(
                        packedPosition >>> Z_SHIFT
                );

        int y =
                (int) (
                        (packedPosition >>> Y_SHIFT)
                                & Y_MASK
                );

        int dimensionLow =
                (int) (
                        (packedPosition >>> DIMENSION_LOW_SHIFT)
                                & FIVE_BIT_MASK
                );

        int dimensionHigh =
                (int) (
                        (packedPosition >>> DIMENSION_HIGH_SHIFT)
                                & FIVE_BIT_MASK
                );

        int dimension =
                (dimensionHigh << 5)
                        | dimensionLow;

        return new ChunkPosition(
                x,
                y,
                z,
                dimension
        );
    }

    private static int decodeSigned21(
            long shiftedValue
    ) {
        long raw =
                shiftedValue
                        & COORD_MASK;

        if ((raw & COORD_SIGN_BIT) != 0) {
            raw -= COORD_RANGE;
        }

        return (int) raw;
    }

    private static void validateGuardBits(
            long packedPosition
    ) {
        long xGuard =
                (packedPosition >>> X_GUARD_SHIFT)
                        & 1L;

        long zGuard =
                (packedPosition >>> Z_GUARD_SHIFT)
                        & 1L;

        long reserved =
                (packedPosition >>> RESERVED_SHIFT)
                        & 1L;

        if (xGuard != 0) {
            throw new IllegalArgumentException(
                    "Invalid ChunkPos: X guard bit is set"
            );
        }

        if (zGuard != 0) {
            throw new IllegalArgumentException(
                    "Invalid ChunkPos: Z guard bit is set"
            );
        }

        if (reserved != 0) {
            throw new IllegalArgumentException(
                    "Invalid ChunkPos: reserved bit is set"
            );
        }
    }
}