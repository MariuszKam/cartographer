package cartographer.model;

import java.util.List;

public record MapChunk(MapChunkCoordinate coordinate, List<MapTile> tiles) {
}
