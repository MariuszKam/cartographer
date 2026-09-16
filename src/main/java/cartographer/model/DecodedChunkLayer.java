package cartographer.model;

import java.util.Arrays;

/**
 * An immutable integer voxel layer with a single-use population builder.
 */
public final class DecodedChunkLayer {
    private final int[] values;

    private DecodedChunkLayer(int[] values) {
        this.values = values;
    }

    public static Builder builder(int length) {
        return new Builder(length);
    }

    public static DecodedChunkLayer empty(int length) {
        return new DecodedChunkLayer(new int[checkedLength(length)]);
    }

    public static DecodedChunkLayer copyOf(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("layer values are required");
        }
        return new DecodedChunkLayer(Arrays.copyOf(values, values.length));
    }

    public int length() {
        return values.length;
    }

    public int valueAt(int index) {
        return values[index];
    }

    public int[] toArray() {
        return Arrays.copyOf(values, values.length);
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
            return new DecodedChunkLayer(ownedValues);
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
