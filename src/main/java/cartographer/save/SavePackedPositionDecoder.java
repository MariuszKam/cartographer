package cartographer.save;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.WorldMetadata;

import java.util.Optional;

final class SavePackedPositionDecoder {

    private SavePackedPositionDecoder() {
    }

    static Optional<MapChunkCoordinate> mainWorldMapChunkCoordinate(Object rawValue) {
        return decode(rawValue)
                .filter(position -> position.dimension() == 0 && position.y() == 0)
                .map(position -> new MapChunkCoordinate(position.x(), position.z()));
    }

    static Optional<MapChunkCoordinate> mapChunkCoordinate(Object rawValue) {
        return decode(rawValue)
                .map(position -> new MapChunkCoordinate(position.x(), position.z()));
    }

    static Optional<ChunkCoordinate> chunkCoordinate(Object rawValue) {
        return decode(rawValue)
                .map(position -> new ChunkCoordinate(
                        position.x(),
                        position.y(),
                        position.z()
                ));
    }

    static Optional<MapRegionCoordinate> mapRegionCoordinate(Object rawValue) {
        return decode(rawValue)
                .map(position -> new MapRegionCoordinate(position.x(), position.z()));
    }

    static Optional<ChunkPosition> decode(Object rawValue) {
        if (!(rawValue instanceof Number number)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChunkPosDecoder.decode(number.longValue()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static boolean mapChunkWithinWorld(
            MapChunkCoordinate coordinate,
            WorldMetadata metadata
    ) {
        long worldX = (long) coordinate.x() * MapChunk.SIZE;
        long worldZ = (long) coordinate.z() * MapChunk.SIZE;
        return worldX >= 0L
                && worldX < metadata.mapSizeX()
                && worldZ >= 0L
                && worldZ < metadata.mapSizeZ();
    }
}
