package cartographer.model;

public record RegionCoordinate(int x, int z) {
    public static final int SIZE_MAP_CHUNKS = 16;

    public static RegionCoordinate fromMapChunk(int mapChunkX, int mapChunkZ) {
        return new RegionCoordinate(Math.floorDiv(mapChunkX, SIZE_MAP_CHUNKS), Math.floorDiv(mapChunkZ, SIZE_MAP_CHUNKS));
    }
}
