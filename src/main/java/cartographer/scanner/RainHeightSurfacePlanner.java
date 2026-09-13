package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RainHeightSurfacePlanner {

    public StreamingSession begin(
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        return new StreamingSession(
                metadata,
                centerWorldX,
                centerWorldZ,
                radius
        );
    }

    public static final class StreamingSession {
        private static final Comparator<RainHeightSurfaceTarget> TARGET_ORDER =
                Comparator.comparingInt(RainHeightSurfaceTarget::worldZ)
                        .thenComparingInt(RainHeightSurfaceTarget::worldX)
                        .thenComparingInt(RainHeightSurfaceTarget::worldY);
        private static final Comparator<MapChunkCoordinate> MAPCHUNK_ORDER =
                Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x);
        private static final Comparator<ChunkPosition> CHUNK_ORDER =
                Comparator.comparingInt(ChunkPosition::y)
                        .thenComparingInt(ChunkPosition::z)
                        .thenComparingInt(ChunkPosition::x)
                        .thenComparingInt(ChunkPosition::dimension);

        private final WorldMetadata metadata;
        private final int centerWorldX;
        private final int centerWorldZ;
        private final long radiusSquared;
        private final Set<MapChunkCoordinate> acceptedMapChunks = new HashSet<>();
        private final List<RainHeightSurfaceTarget> targets = new ArrayList<>();
        private final Set<MapChunkCoordinate> fallbackMapChunks = new HashSet<>();

        private StreamingSession(
                WorldMetadata metadata,
                int centerWorldX,
                int centerWorldZ,
                int radius
        ) {
            this.metadata = metadata;
            this.centerWorldX = centerWorldX;
            this.centerWorldZ = centerWorldZ;
            this.radiusSquared = (long) radius * radius;
        }

        public void accept(MapChunk mapChunk) {
            Objects.requireNonNull(mapChunk, "mapChunk is required");
            MapChunkCoordinate coordinate = mapChunk.coordinate();
            if (!acceptedMapChunks.add(coordinate)) {
                return;
            }

            if (!mapChunk.hasRainHeightMap()) {
                fallbackMapChunks.add(coordinate);
                return;
            }

            List<RainHeightSurfaceTarget> localTargets = new ArrayList<>();
            for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                    int worldX = coordinate.x() * MapChunk.SIZE + localX;
                    int worldZ = coordinate.z() * MapChunk.SIZE + localZ;
                    if (!inWorld(worldX, worldZ)
                            || !withinRadius(worldX, worldZ)) {
                        continue;
                    }

                    int worldY = mapChunk.rainHeightAt(localX, localZ);
                    if (worldY < 0 || worldY >= metadata.mapSizeY()) {
                        fallbackMapChunks.add(coordinate);
                        return;
                    }
                    localTargets.add(
                            new RainHeightSurfaceTarget(worldX, worldY, worldZ)
                    );
                }
            }
            targets.addAll(localTargets);
        }

        public RainHeightSurfacePlan finish() {
            List<RainHeightSurfaceTarget> orderedTargets = targets.stream()
                    .sorted(TARGET_ORDER)
                    .toList();
            List<MapChunkCoordinate> orderedFallbacks = fallbackMapChunks.stream()
                    .sorted(MAPCHUNK_ORDER)
                    .toList();
            List<ChunkPosition> positions = orderedTargets.stream()
                    .map(RainHeightSurfaceTarget::chunkPosition)
                    .distinct()
                    .sorted(CHUNK_ORDER)
                    .toList();
            return new RainHeightSurfacePlan(
                    orderedTargets,
                    positions,
                    orderedFallbacks
            );
        }

        private boolean inWorld(int worldX, int worldZ) {
            return worldX >= 0
                    && worldX < metadata.mapSizeX()
                    && worldZ >= 0
                    && worldZ < metadata.mapSizeZ();
        }

        private boolean withinRadius(int worldX, int worldZ) {
            long dx = (long) worldX - centerWorldX;
            long dz = (long) worldZ - centerWorldZ;
            return dx * dx + dz * dz <= radiusSquared;
        }
    }
}
