package cartographer.model;

public record ChunkCoordinate(int x, int z) {
    public static final int SIZE_BLOCKS = 32;

    public static ChunkCoordinate fromWorld(double worldX, double worldZ) {
        return new ChunkCoordinate(floorDiv(worldX, SIZE_BLOCKS), floorDiv(worldZ, SIZE_BLOCKS));
    }

    private static int floorDiv(double value, int divisor) {
        return Math.floorDiv((int) Math.floor(value), divisor);
    }
}
