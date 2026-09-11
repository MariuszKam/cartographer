package cartographer.model;

public record MapChunkCoordinate(int x, int z) {
    public static final int SIZE_BLOCKS = 32;

    public static MapChunkCoordinate fromWorld(double worldX, double worldZ) {
        return new MapChunkCoordinate(floorDiv(worldX, SIZE_BLOCKS), floorDiv(worldZ, SIZE_BLOCKS));
    }

    public RegionCoordinate regionCoordinate() {
        return RegionCoordinate.fromMapChunk(x, z);
    }

    private static int floorDiv(double value, int divisor) {
        return Math.floorDiv((int) Math.floor(value), divisor);
    }
}
