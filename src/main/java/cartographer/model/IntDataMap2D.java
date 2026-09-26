package cartographer.model;

import java.util.Arrays;

public record IntDataMap2D(
        int size,
        int topLeftPadding,
        int bottomRightPadding,
        int[] data
) {
    public IntDataMap2D {
        if (size <= 0) {
            throw new IllegalArgumentException(
                    "IntDataMap2D size must be positive"
            );
        }

        if (topLeftPadding < 0) {
            throw new IllegalArgumentException(
                    "IntDataMap2D topLeftPadding must be non-negative"
            );
        }

        if (bottomRightPadding < 0) {
            throw new IllegalArgumentException(
                    "IntDataMap2D bottomRightPadding must be non-negative"
            );
        }

        if (topLeftPadding
                + bottomRightPadding
                > size) {

            throw new IllegalArgumentException(
                    "IntDataMap2D padding must not exceed size"
            );
        }

        int expected =
                Math.multiplyExact(
                        size,
                        size
                );

        if (data == null
                || data.length != expected) {

            throw new IllegalArgumentException(
                    "IntDataMap2D data length must be "
                            + expected
            );
        }

        data =
                Arrays.copyOf(
                        data,
                        data.length
                );
    }

    public int valueAt(
            int localX,
            int localZ
    ) {
        if (localX < 0
                || localX >= size
                || localZ < 0
                || localZ >= size) {

            throw new IndexOutOfBoundsException(
                    "IntDataMap2D coordinate out of bounds"
            );
        }

        return data[
                localZ * size
                        + localX
                ];
    }

    public int innerSize() {
        return size
                - topLeftPadding
                - bottomRightPadding;
    }

    public int innerMin() {
        return topLeftPadding;
    }

    public int innerMaxExclusive() {
        return size
                - bottomRightPadding;
    }

    public int[] data() {
        return Arrays.copyOf(
                data,
                data.length
        );
    }
}