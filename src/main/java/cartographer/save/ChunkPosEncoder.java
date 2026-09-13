package cartographer.save;

import cartographer.model.ChunkPosition;

import java.util.Objects;

public final class ChunkPosEncoder {

    private static final long COORD_MASK =
            0x1FFFFFL;

    private static final int X_SHIFT =
            0;

    private static final int DIMENSION_LOW_SHIFT =
            22;

    private static final int Z_SHIFT =
            27;

    private static final int DIMENSION_HIGH_SHIFT =
            49;

    private static final int Y_SHIFT =
            54;

    private ChunkPosEncoder() {
    }

    public static long encode(
            ChunkPosition position
    ) {
        Objects.requireNonNull(
                position,
                "position is required"
        );

        return encode(
                position.x(),
                position.y(),
                position.z(),
                position.dimension()
        );
    }

    public static long encode(
            int x,
            int y,
            int z,
            int dimension
    ) {
        validateCoordinate(
                x,
                "x"
        );
        validateCoordinate(
                z,
                "z"
        );

        if (y < 0 || y > 511) {
            throw new IllegalArgumentException(
                    "y must be between 0 and 511: " + y
            );
        }

        if (dimension < 0 || dimension > 1023) {
            throw new IllegalArgumentException(
                    "dimension must be between 0 and 1023: "
                            + dimension
            );
        }

        long dimensionLow =
                dimension & 0x1FL;

        long dimensionHigh =
                (dimension >>> 5) & 0x1FL;

        return (x & COORD_MASK) << X_SHIFT
                | dimensionLow << DIMENSION_LOW_SHIFT
                | (z & COORD_MASK) << Z_SHIFT
                | dimensionHigh << DIMENSION_HIGH_SHIFT
                | (long) y << Y_SHIFT;
    }

    private static void validateCoordinate(
            int coordinate,
            String name
    ) {
        if (coordinate < -1_048_576
                || coordinate > 1_048_575) {
            throw new IllegalArgumentException(
                    name
                            + " must be between -1048576 and 1048575: "
                            + coordinate
            );
        }
    }
}
