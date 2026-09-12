package cartographer.model;

public record MapRegionCoordinate(
        int x,
        int z
) {
    public static final int SIZE_MAP_CHUNKS =
            16;

    public static MapRegionCoordinate fromMapChunk(
            int mapChunkX,
            int mapChunkZ
    ) {
        return new MapRegionCoordinate(
                Math.floorDiv(
                        mapChunkX,
                        SIZE_MAP_CHUNKS
                ),
                Math.floorDiv(
                        mapChunkZ,
                        SIZE_MAP_CHUNKS
                )
        );
    }
}
