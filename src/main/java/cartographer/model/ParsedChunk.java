package cartographer.model;

import java.util.Arrays;

public record ParsedChunk(
        ChunkCoordinate coordinate,
        int minY,
        int sizeX,
        int sizeY,
        int sizeZ,
        int[] blockIds,
        int[] liquidIds,
        int savedCompressionVersion,
        boolean liquidLayerAvailable,
        String liquidDecodeError
) {
    public ParsedChunk(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] blockIds
    ) {
        this(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockIds,
                new int[
                        sizeX
                                * sizeY
                                * sizeZ
                        ],
                0,
                true,
                ""
        );
    }

    public ParsedChunk(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] blockIds,
            int[] liquidIds,
            int savedCompressionVersion
    ) {
        this(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockIds,
                liquidIds,
                savedCompressionVersion,
                true,
                ""
        );
    }

    public ParsedChunk {
        blockIds =
                copyAndValidateLayer(
                        blockIds,
                        sizeX,
                        sizeY,
                        sizeZ,
                        "block"
                );

        liquidDecodeError =
                liquidDecodeError == null
                        ? ""
                        : liquidDecodeError;

        if (!liquidLayerAvailable
                && liquidIds != null) {
            throw new IllegalArgumentException(
                    "unavailable liquid layer must not contain liquid values"
            );
        }

        liquidIds =
                liquidLayerAvailable
                        ? copyAndValidateLayer(
                                liquidIds,
                                sizeX,
                                sizeY,
                                sizeZ,
                                "liquid"
                        )
                        : null;

        if (liquidLayerAvailable
                && !liquidDecodeError.isBlank()) {
            throw new IllegalArgumentException(
                    "available liquid layer cannot have a decode error"
            );
        }

        if (!liquidLayerAvailable
                && liquidDecodeError.isBlank()) {
            throw new IllegalArgumentException(
                    "unavailable liquid layer must have a decode error"
            );
        }
    }

    public int blockIdAt(
            int x,
            int y,
            int z
    ) {
        if (x < 0
                || x >= sizeX
                || y < 0
                || y >= sizeY
                || z < 0
                || z >= sizeZ) {

            throw new IndexOutOfBoundsException(
                    "Chunk coordinate out of bounds"
            );
        }

        return blockIds[
                (y * sizeZ + z)
                        * sizeX
                        + x
                ];
    }

    public int liquidIdAt(
            int x,
            int y,
            int z
    ) {
        if (x < 0
                || x >= sizeX
                || y < 0
                || y >= sizeY
                || z < 0
                || z >= sizeZ) {

            throw new IndexOutOfBoundsException(
                    "Chunk coordinate out of bounds"
            );
        }

        requireLiquidLayerAvailable();

        return liquidIds[
                (y * sizeZ + z)
                        * sizeX
                        + x
                ];
    }

    public int worldX(
            int localX
    ) {
        return coordinate.x()
                * ChunkCoordinate.SIZE_BLOCKS
                + localX;
    }

    public int worldZ(
            int localZ
    ) {
        return coordinate.z()
                * ChunkCoordinate.SIZE_BLOCKS
                + localZ;
    }

    public int worldY(
            int localY
    ) {
        return minY
                + localY;
    }

    public int[] blockIds() {
        return Arrays.copyOf(
                blockIds,
                blockIds.length
        );
    }

    public int[] liquidIds() {
        requireLiquidLayerAvailable();

        return Arrays.copyOf(
                liquidIds,
                liquidIds.length
        );
    }

    private void requireLiquidLayerAvailable() {
        if (!liquidLayerAvailable) {
            throw new IllegalStateException(
                    "liquid layer is unavailable: "
                            + liquidDecodeError
            );
        }
    }

    private static int[] copyAndValidateLayer(
            int[] values,
            int sizeX,
            int sizeY,
            int sizeZ,
            String name
    ) {
        int expected =
                sizeX
                        * sizeY
                        * sizeZ;

        if (values == null
                || values.length
                != expected) {

            throw new IllegalArgumentException(
                    name
                            + " layer must contain "
                            + expected
                            + " values"
            );
        }

        return Arrays.copyOf(
                values,
                values.length
        );
    }
}
