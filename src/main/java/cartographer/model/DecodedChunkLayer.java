package cartographer.model;

import java.util.Arrays;

/**
 * Immutable integer voxel layer.
 *
 * <p>The layer can own one of three representations:</p>
 * <ul>
 *     <li>a materialized int array;</li>
 *     <li>a single constant value;</li>
 *     <li>a compact palette plus decoded bit planes.</li>
 * </ul>
 *
 * <p>Public array access always materializes a defensive copy. Internal
 * point lookups can therefore avoid a 32^3 int array for constant layers and
 * for the Surface-oriented compact decode path.</p>
 */
public final class DecodedChunkLayer {
    private final int[] values;
    private final int length;
    private final int constantValue;
    private final int[] compactPalette;
    private final byte[] compactBitPlanes;
    private final int compactBitSize;
    private final int compactSliceCount;
    private final int compactValuesPerSlice;

    private DecodedChunkLayer(
            int[] values,
            int length,
            int constantValue,
            int[] compactPalette,
            byte[] compactBitPlanes,
            int compactBitSize,
            int compactSliceCount,
            int compactValuesPerSlice
    ) {
        this.values = values;
        this.length = checkedLength(length);
        this.constantValue = constantValue;
        this.compactPalette = compactPalette;
        this.compactBitPlanes = compactBitPlanes;
        this.compactBitSize = compactBitSize;
        this.compactSliceCount = compactSliceCount;
        this.compactValuesPerSlice = compactValuesPerSlice;
    }

    public static Builder builder(int length) {
        return new Builder(length);
    }

    public static DecodedChunkLayer empty(int length) {
        return constant(length, 0);
    }

    public static DecodedChunkLayer constant(
            int length,
            int value
    ) {
        return new DecodedChunkLayer(
                null,
                length,
                value,
                null,
                null,
                0,
                0,
                0
        );
    }

    public static DecodedChunkLayer copyOf(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException(
                    "layer values are required"
            );
        }
        return materialized(
                Arrays.copyOf(
                        values,
                        values.length
                )
        );
    }

    /**
     * Creates an owned compact palette/bit-plane layer.
     *
     * <p>The supplied buffers may come from reusable decoder scratch and are
     * therefore copied exactly once into the published immutable layer.</p>
     */
    public static DecodedChunkLayer compactPaletteBits(
            int length,
            int[] palette,
            int paletteLength,
            byte[] bitPlanes,
            int bitPlaneLength,
            int bitSize,
            int sliceCount,
            int valuesPerSlice
    ) {
        int checkedLength = checkedLength(length);
        if (palette == null) {
            throw new IllegalArgumentException(
                    "compact palette is required"
            );
        }
        if (paletteLength <= 0
                || paletteLength > palette.length) {
            throw new IllegalArgumentException(
                    "compact palette length is invalid"
            );
        }
        if (bitPlanes == null) {
            throw new IllegalArgumentException(
                    "compact bit planes are required"
            );
        }
        if (bitSize <= 0) {
            throw new IllegalArgumentException(
                    "compact bit size must be positive"
            );
        }
        if (sliceCount <= 0) {
            throw new IllegalArgumentException(
                    "compact slice count must be positive"
            );
        }
        if (valuesPerSlice <= 0
                || valuesPerSlice > Integer.SIZE) {
            throw new IllegalArgumentException(
                    "compact values per slice must be between 1 and 32"
            );
        }
        if (Math.multiplyExact(
                sliceCount,
                valuesPerSlice
        ) != checkedLength) {
            throw new IllegalArgumentException(
                    "compact layer geometry does not match length"
            );
        }

        int expectedBitPlaneLength =
                Math.multiplyExact(
                        Math.multiplyExact(
                                bitSize,
                                sliceCount
                        ),
                        Integer.BYTES
                );
        if (bitPlaneLength != expectedBitPlaneLength
                || bitPlaneLength > bitPlanes.length) {
            throw new IllegalArgumentException(
                    "compact bit-plane length is invalid"
            );
        }

        return new DecodedChunkLayer(
                null,
                checkedLength,
                0,
                Arrays.copyOf(
                        palette,
                        paletteLength
                ),
                Arrays.copyOf(
                        bitPlanes,
                        bitPlaneLength
                ),
                bitSize,
                sliceCount,
                valuesPerSlice
        );
    }

    public int length() {
        return length;
    }

    public int valueAt(int index) {
        checkIndex(index);

        if (values != null) {
            return values[index];
        }
        if (compactPalette == null) {
            return constantValue;
        }

        int slice =
                index / compactValuesPerSlice;
        int valueOffset =
                index % compactValuesPerSlice;
        int paletteIndex = 0;

        for (int bit = 0;
             bit < compactBitSize;
             bit++) {
            int dataBits =
                    readLittleEndianInt(
                            compactBitPlanes,
                            (bit
                                    * compactSliceCount
                                    + slice)
                                    * Integer.BYTES
                    );
            paletteIndex |=
                    ((dataBits >>> valueOffset) & 1)
                            << bit;
        }

        if (paletteIndex >= compactPalette.length) {
            throw new IllegalStateException(
                    "compact palette index out of range: "
                            + paletteIndex
            );
        }
        return compactPalette[paletteIndex];
    }

    public int[] toArray() {
        if (values != null) {
            return Arrays.copyOf(
                    values,
                    values.length
            );
        }

        int[] materialized =
                new int[length];

        if (compactPalette == null) {
            if (constantValue != 0) {
                Arrays.fill(
                        materialized,
                        constantValue
                );
            }
            return materialized;
        }

        for (int index = 0;
             index < length;
             index++) {
            materialized[index] =
                    valueAt(index);
        }
        return materialized;
    }

    public static final class Builder {
        private int[] values;

        private Builder(int length) {
            values =
                    new int[
                            checkedLength(length)
                    ];
        }

        public Builder set(
                int index,
                int value
        ) {
            writableValues()[index] =
                    value;
            return this;
        }

        public DecodedChunkLayer build() {
            int[] ownedValues =
                    writableValues();
            values = null;
            return materialized(
                    ownedValues
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

    private static DecodedChunkLayer materialized(
            int[] values
    ) {
        return new DecodedChunkLayer(
                values,
                values.length,
                0,
                null,
                null,
                0,
                0,
                0
        );
    }

    private void checkIndex(int index) {
        if (index < 0
                || index >= length) {
            throw new IndexOutOfBoundsException(
                    "decoded chunk layer index out of bounds: "
                            + index
            );
        }
    }

    private static int readLittleEndianInt(
            byte[] bytes,
            int offset
    ) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
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
