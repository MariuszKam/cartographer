package cartographer.model;

public record WorldPosition(double x, double y, double z) {
    public ChunkCoordinate chunkCoordinate() {
        return ChunkCoordinate.fromWorld(x, z);
    }

    public MapChunkCoordinate mapChunkCoordinate() {
        return MapChunkCoordinate.fromWorld(x, z);
    }
}
