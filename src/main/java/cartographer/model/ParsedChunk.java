package cartographer.model;

public record ParsedChunk(ChunkCoordinate coordinate, int minY, int sizeX, int sizeY, int sizeZ, int[] blockIds) {
    public int blockIdAt(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("Chunk coordinate out of bounds");
        }
        return blockIds[(y * sizeZ + z) * sizeX + x];
    }

    public int worldX(int localX) {
        return coordinate.x() * ChunkCoordinate.SIZE_BLOCKS + localX;
    }

    public int worldZ(int localZ) {
        return coordinate.z() * ChunkCoordinate.SIZE_BLOCKS + localZ;
    }
}
