package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SurfaceFallbackChunkPlanner {

    private static final Comparator<ChunkPosition> ORDER =
            Comparator.comparingInt(ChunkPosition::y)
                    .thenComparingInt(ChunkPosition::z)
                    .thenComparingInt(ChunkPosition::x)
                    .thenComparingInt(ChunkPosition::dimension);

    public List<ChunkPosition> plan(
            WorldMetadata metadata,
            Collection<MapChunkCoordinate> mapChunks
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(mapChunks, "mapChunks is required");

        Set<MapChunkCoordinate> uniqueMapChunks = new HashSet<>();
        for (MapChunkCoordinate mapChunk : mapChunks) {
            uniqueMapChunks.add(
                    Objects.requireNonNull(
                            mapChunk,
                            "mapChunks cannot contain null"
                    )
            );
        }

        if (metadata.mapSizeY() <= 0 || uniqueMapChunks.isEmpty()) {
            return List.of();
        }

        int verticalChunkCount =
                (metadata.mapSizeY() - 1) / ChunkCoordinate.SIZE_BLOCKS + 1;
        List<ChunkPosition> positions = new ArrayList<>();
        for (MapChunkCoordinate mapChunk : uniqueMapChunks) {
            for (int y = 0; y < verticalChunkCount; y++) {
                positions.add(
                        new ChunkPosition(
                                mapChunk.x(),
                                y,
                                mapChunk.z(),
                                0
                        )
                );
            }
        }

        return positions.stream()
                .sorted(ORDER)
                .toList();
    }
}
