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
                new int[sizeX * sizeY * sizeZ],
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

        liquidIds =
                copyAndValidateLayer(
                        liquidIds,
                        sizeX,
                        sizeY,
                        sizeZ,
                        "liquid"
                );

        liquidDecodeError =
                liquidDecodeError == null
                        ? ""
                        : liquidDecodeError;
    }

    public int blockIdAt(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("Chunk coordinate out of bounds");
        }
        return blockIds[(y * sizeZ + z) * sizeX + x];
    }

    public int liquidIdAt(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("Chunk coordinate out of bounds");
        }
        return liquidIds[(y * sizeZ + z) * sizeX + x];
    }

    public boolean hasLiquidAt(int x, int y, int z) {
        return liquidIdAt(x, y, z) != 0;
    }

    public int worldX(int localX) {
        return coordinate.x() * ChunkCoordinate.SIZE_BLOCKS + localX;
    }

    public int worldZ(int localZ) {
        return coordinate.z() * ChunkCoordinate.SIZE_BLOCKS + localZ;
    }

    public int worldY(int localY) {
        return minY + localY;
    }

    public int[] blockIds() {
        return Arrays.copyOf(
                blockIds,
                blockIds.length
        );
    }

    public int[] liquidIds() {
        return Arrays.copyOf(
                liquidIds,
                liquidIds.length
        );
    }

    private static int[] copyAndValidateLayer(
            int[] values,
            int sizeX,
            int sizeY,
            int sizeZ,
            String name
    ) {
        int expected =
                sizeX * sizeY * sizeZ;

        if (values == null || values.length != expected) {
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
