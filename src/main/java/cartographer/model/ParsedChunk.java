package cartographer.model;

import java.util.Objects;

public final class ParsedChunk {
    private final ChunkCoordinate coordinate;
    private final int minY;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final DecodedChunkLayer blockLayer;
    private final DecodedChunkLayer liquidLayer;
    private final int savedCompressionVersion;
    private final boolean liquidLayerAvailable;
    private final String liquidDecodeError;

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

    public ParsedChunk(
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
        this(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                DecodedChunkLayer.copyOf(blockIds),
                layerFromArray(liquidIds, liquidLayerAvailable),
                savedCompressionVersion,
                liquidLayerAvailable,
                liquidDecodeError
        );
    }

    private ParsedChunk(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            DecodedChunkLayer blockLayer,
            DecodedChunkLayer liquidLayer,
            int savedCompressionVersion,
            boolean liquidLayerAvailable,
            String liquidDecodeError
    ) {
        int expectedLength = expectedLayerLength(sizeX, sizeY, sizeZ);
        if (blockLayer == null
                || blockLayer.length() != expectedLength) {
            throw new IllegalArgumentException(
                    "block layer must contain " + expectedLength + " values"
            );
        }

        String normalizedLiquidDecodeError =
                liquidDecodeError == null ? "" : liquidDecodeError;

        if (liquidLayerAvailable) {
            if (liquidLayer == null
                    || liquidLayer.length() != expectedLength) {
                throw new IllegalArgumentException(
                        "liquid layer must contain " + expectedLength + " values"
                );
            }
            if (!normalizedLiquidDecodeError.isBlank()) {
                throw new IllegalArgumentException(
                        "available liquid layer cannot have a decode error"
                );
            }
        } else {
            if (liquidLayer != null) {
                throw new IllegalArgumentException(
                        "unavailable liquid layer must not contain liquid values"
                );
            }
            if (normalizedLiquidDecodeError.isBlank()) {
                throw new IllegalArgumentException(
                        "unavailable liquid layer must have a decode error"
                );
            }
        }

        this.coordinate = coordinate;
        this.minY = minY;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blockLayer = blockLayer;
        this.liquidLayer = liquidLayer;
        this.savedCompressionVersion = savedCompressionVersion;
        this.liquidLayerAvailable = liquidLayerAvailable;
        this.liquidDecodeError = normalizedLiquidDecodeError;
    }

    public static ParsedChunk fromDecodedLayers(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            DecodedChunkLayer blockLayer,
            DecodedChunkLayer liquidLayer,
            int savedCompressionVersion,
            boolean liquidLayerAvailable,
            String liquidDecodeError
    ) {
        return new ParsedChunk(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockLayer,
                liquidLayer,
                savedCompressionVersion,
                liquidLayerAvailable,
                liquidDecodeError
        );
    }

    public ChunkCoordinate coordinate() {
        return coordinate;
    }

    public int minY() {
        return minY;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int savedCompressionVersion() {
        return savedCompressionVersion;
    }

    public boolean liquidLayerAvailable() {
        return liquidLayerAvailable;
    }

    public String liquidDecodeError() {
        return liquidDecodeError;
    }

    public int blockIdAt(int x, int y, int z) {
        return blockLayer.valueAt(indexAt(x, y, z));
    }

    public int liquidIdAt(int x, int y, int z) {
        int index = indexAt(x, y, z);
        requireLiquidLayerAvailable();
        return liquidLayer.valueAt(index);
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
        return blockLayer.toArray();
    }

    public int[] liquidIds() {
        requireLiquidLayerAvailable();
        return liquidLayer.toArray();
    }

    private int indexAt(int x, int y, int z) {
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
        return (y * sizeZ + z) * sizeX + x;
    }

    private void requireLiquidLayerAvailable() {
        if (!liquidLayerAvailable) {
            throw new IllegalStateException(
                    "liquid layer is unavailable: " + liquidDecodeError
            );
        }
    }

    private static int expectedLayerLength(int sizeX, int sizeY, int sizeZ) {
        return sizeX * sizeY * sizeZ;
    }

    private static DecodedChunkLayer layerFromArray(
            int[] values,
            boolean available
    ) {
        if (!available && values != null) {
            throw new IllegalArgumentException(
                    "unavailable liquid layer must not contain liquid values"
            );
        }
        return available ? DecodedChunkLayer.copyOf(values) : null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParsedChunk chunk)) {
            return false;
        }
        return minY == chunk.minY
                && sizeX == chunk.sizeX
                && sizeY == chunk.sizeY
                && sizeZ == chunk.sizeZ
                && savedCompressionVersion == chunk.savedCompressionVersion
                && liquidLayerAvailable == chunk.liquidLayerAvailable
                && Objects.equals(coordinate, chunk.coordinate)
                && Objects.equals(blockLayer, chunk.blockLayer)
                && Objects.equals(liquidLayer, chunk.liquidLayer)
                && Objects.equals(liquidDecodeError, chunk.liquidDecodeError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockLayer,
                liquidLayer,
                savedCompressionVersion,
                liquidLayerAvailable,
                liquidDecodeError
        );
    }

    @Override
    public String toString() {
        return "ParsedChunk[coordinate=" + coordinate
                + ", minY=" + minY
                + ", sizeX=" + sizeX
                + ", sizeY=" + sizeY
                + ", sizeZ=" + sizeZ
                + ", blockLayerLength=" + blockLayer.length()
                + ", liquidLayerLength="
                + (liquidLayer == null ? "null" : liquidLayer.length())
                + ", savedCompressionVersion=" + savedCompressionVersion
                + ", liquidLayerAvailable=" + liquidLayerAvailable
                + ", liquidDecodeError=" + liquidDecodeError
                + "]";
    }
}
