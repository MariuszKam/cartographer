package cartographer.model;

public record ChunkCoordinate(
        int x,
        int y,
        int z
) {
    public static final int SIZE_BLOCKS =
            32;

    public ChunkCoordinate(
            int x,
            int z
    ) {
        this(
                x,
                0,
                z
        );
    }

    public static ChunkCoordinate fromWorld(
            double worldX,
            double worldZ
    ) {
        return new ChunkCoordinate(
                floorDiv(
                        worldX
                ),
                0,
                floorDiv(
                        worldZ
                )
        );
    }

    private static int floorDiv(
            double value
    ) {
        return Math.floorDiv(
                (int) Math.floor(
                        value
                ),
                SIZE_BLOCKS
        );
    }
}