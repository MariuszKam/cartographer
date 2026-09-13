package cartographer.scanner;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SurfaceObjectPlanner {
    private static final Comparator<SurfaceObjectTarget> TARGET_ORDER =
            Comparator.comparingInt(SurfaceObjectTarget::worldZ)
                    .thenComparingInt(SurfaceObjectTarget::worldX);
    private static final Comparator<ChunkPosition> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPosition::y)
                    .thenComparingInt(ChunkPosition::z)
                    .thenComparingInt(ChunkPosition::x)
                    .thenComparingInt(ChunkPosition::dimension);

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
        return new StreamingSession(metadata, centerWorldX, centerWorldZ, radius);
    }

    public static final class StreamingSession {
        private final WorldMetadata metadata;
        private final int centerWorldX;
        private final int centerWorldZ;
        private final long radiusSquared;
        private final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();

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
            mapChunks.putIfAbsent(mapChunk.coordinate(), mapChunk);
        }

        public SurfaceObjectPlan finish() {
            List<SurfaceObjectTarget> targets = new ArrayList<>();
            for (MapChunk mapChunk : mapChunks.values()) {
                if (!mapChunk.hasWorldGenTerrainHeightMap()
                        && !mapChunk.hasRainHeightMap()) {
                    continue;
                }
                MapChunkCoordinate coordinate = mapChunk.coordinate();
                int[] worldGenHeights = mapChunk.worldGenTerrainHeightMap();
                for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                    for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                        int worldX = coordinate.x() * MapChunk.SIZE + localX;
                        int worldZ = coordinate.z() * MapChunk.SIZE + localZ;
                        if (!inWorld(worldX, worldZ) || !withinRadius(worldX, worldZ)) {
                            continue;
                        }
                        Set<Integer> candidates = new HashSet<>();
                        if (mapChunk.hasWorldGenTerrainHeightMap()) {
                            int terrain = worldGenHeights[localZ * MapChunk.SIZE + localX];
                            addRange(candidates, terrain - 2, terrain + 4);
                        }
                        if (mapChunk.hasRainHeightMap()) {
                            int rain = mapChunk.rainHeightAt(localX, localZ);
                            addRange(candidates, rain, rain + 4);
                        }
                        candidates.removeIf(y -> y < 0 || y >= metadata.mapSizeY());
                        if (!candidates.isEmpty()) {
                            targets.add(new SurfaceObjectTarget(
                                    worldX,
                                    worldZ,
                                    candidates.stream().sorted().toList()
                            ));
                        }
                    }
                }
            }
            targets.sort(TARGET_ORDER);
            Set<ChunkPosition> positions = new HashSet<>();
            for (SurfaceObjectTarget target : targets) {
                for (int worldY : target.candidateWorldYs()) {
                    positions.add(target.chunkPosition(worldY));
                }
            }
            return new SurfaceObjectPlan(
                    targets,
                    positions.stream().sorted(CHUNK_ORDER).toList()
            );
        }

        private void addRange(Set<Integer> values, int startInclusive, int endExclusive) {
            for (int y = startInclusive; y < endExclusive; y++) {
                values.add(y);
            }
        }

        private boolean inWorld(int worldX, int worldZ) {
            return worldX >= 0 && worldX < metadata.mapSizeX()
                    && worldZ >= 0 && worldZ < metadata.mapSizeZ();
        }

        private boolean withinRadius(int worldX, int worldZ) {
            long dx = (long) worldX - centerWorldX;
            long dz = (long) worldZ - centerWorldZ;
            return dx * dx + dz * dz <= radiusSquared;
        }
    }
}
