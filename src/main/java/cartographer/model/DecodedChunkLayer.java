package cartographer.model;

import java.util.Arrays;

/**
 * An immutable integer voxel layer with a single-use population builder.
 *
 * <p>Uniform layers use a compact constant representation and only
 * materialize an int array when {@link #toArray()} is explicitly requested.
 * This keeps public defensive-copy semantics while avoiding a 32^3 int array
 * for empty liquids and uniform chunks on internal hot paths.</p>
 */
public final class DecodedChunkLayer {
    private final int[] values;
    private final int length;
    private final int constantValue;

    private DecodedChunkLayer(
            int[] values,
            int length,
            int constantValue
    ) {
        this.values = values;
        this.length = checkedLength(length);
        this.constantValue = constantValue;
    }

    public static Builder builder(int length) {
        return new Builder(length);
    }

    public static DecodedChunkLayer empty(int length) {
        return constant(length, 0);
    }

    public static DecodedChunkLayer constant(int length, int value) {
        return new DecodedChunkLayer(null, length, value);
    }

    public static DecodedChunkLayer copyOf(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("layer values are required");
        }
        return new DecodedChunkLayer(
                Arrays.copyOf(values, values.length),
                values.length,
                0
        );
    }

    public int length() {
        return length;
    }

    public int valueAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "decoded chunk layer index out of bounds: " + index
            );
        }
        return values == null ? constantValue : values[index];
    }

    public int[] toArray() {
        if (values != null) {
            return Arrays.copyOf(values, values.length);
        }
        int[] materialized = new int[length];
        if (constantValue != 0) {
            Arrays.fill(materialized, constantValue);
        }
        return materialized;
    }

    public static final class Builder {
        private int[] values;

        private Builder(int length) {
            values = new int[checkedLength(length)];
        }

        public Builder set(int index, int value) {
            writableValues()[index] = value;
            return this;
        }

        public Builder fill(int value) {
            Arrays.fill(writableValues(), value);
            return this;
        }

        public DecodedChunkLayer build() {
            int[] ownedValues = writableValues();
            values = null;
            return new DecodedChunkLayer(
                    ownedValues,
                    ownedValues.length,
                    0
            );
        }

        private int[] writableValues() {
            if (values == null) {
                throw new IllegalStateException(
                        "decoded chunk layer builder is already built"
                );
            }
            return values;
        }
    }

    private static int checkedLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "layer length must not be negative"
            );
        }
        return length;
    }
}
