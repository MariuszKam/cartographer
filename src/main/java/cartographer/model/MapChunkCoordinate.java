package cartographer.model;

public record MapChunkCoordinate(
        int x,
        int z
) {
    public static final int SIZE_BLOCKS =
            32;

    public static MapChunkCoordinate fromWorld(
            double worldX,
            double worldZ
    ) {
        return new MapChunkCoordinate(
                floorDiv(
                        worldX
                ),
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