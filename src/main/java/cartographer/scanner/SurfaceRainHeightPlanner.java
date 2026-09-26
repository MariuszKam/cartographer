package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunkHeightView;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Tile-local RainHeight planner for the Surface rain-height fast path. */
public final class SurfaceRainHeightPlanner {
    public StreamingSession begin(
            WorldMetadata metadata,
            double centerX,
            double centerZ,
            int radius
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                centerX, centerZ, radius, metadata);
        return new StreamingSession(metadata, layout);
    }

    public StreamingSession beginForMapChunks(
            WorldMetadata metadata,
            java.util.Collection<MapChunkCoordinate> mapChunks
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        return new StreamingSession(
                metadata,
                SurfaceTileLayout.forMapChunks(mapChunks, metadata)
        );
    }

    public static final class StreamingSession {
        private static final Comparator<ChunkPosition> CHUNK_ORDER =
                Comparator.comparingInt(ChunkPosition::y)
                        .thenComparingInt(ChunkPosition::z)
                        .thenComparingInt(ChunkPosition::x)
                        .thenComparingInt(ChunkPosition::dimension);
        private static final Comparator<MapChunkCoordinate> MAPCHUNK_ORDER =
                Comparator.comparingInt(MapChunkCoordinate::z)
                        .thenComparingInt(MapChunkCoordinate::x);

        private final WorldMetadata metadata;
        private final SurfaceTileLayout layout;
        private final int[][] rainHeights;
        private final boolean[][] candidatePresent;
        private final boolean[] accepted;
        private final boolean[] promoted;
        private final Set<Integer> deliveredTiles = new HashSet<>();
        private boolean finished;

        private StreamingSession(WorldMetadata metadata, SurfaceTileLayout layout) {
            this.metadata = metadata;
            this.layout = layout;
            this.rainHeights = new int[layout.tileCount()][];
            this.candidatePresent = new boolean[layout.tileCount()][];
            this.accepted = new boolean[layout.tileCount()];
            this.promoted = new boolean[layout.tileCount()];
            for (int tileIndex = 0; tileIndex < layout.tileCount(); tileIndex++) {
                int cells = layout.tileCellCount(
                        layout.tileXAt(tileIndex), layout.tileZAt(tileIndex));
                rainHeights[tileIndex] = new int[cells];
                candidatePresent[tileIndex] = new boolean[cells];
            }
        }

        public void accept(MapChunkHeightView mapChunk) {
            ensureMutable();
            Objects.requireNonNull(mapChunk, "mapchunk is required");
            int tileIndex = tileIndex(mapChunk.coordinate());
            if (tileIndex < 0 || !deliveredTiles.add(tileIndex)) {
                return;
            }
            accepted[tileIndex] = true;
            if (!mapChunk.hasRainHeight()) {
                promote(tileIndex);
                return;
            }

            int tileX = layout.tileXAt(tileIndex);
            int tileZ = layout.tileZAt(tileIndex);
            for (int localZ = 0; localZ < layout.tileHeight(tileZ); localZ++) {
                for (int localX = 0; localX < layout.tileWidth(tileX); localX++) {
                    int worldX = layout.worldXForTileLocal(tileX, localX);
                    int worldZ = layout.worldZForTileLocal(tileZ, localZ);
                    if (!layout.isActive(worldX, worldZ)) {
                        continue;
                    }
                    int height = mapChunk.rainHeightAt(localX, localZ);
                    if (height < 0 || height >= metadata.mapSizeY()) {
                        promote(tileIndex);
                        return;
                    }
                    int cellIndex = layout.cellIndex(tileX, tileZ, localX, localZ);
                    rainHeights[tileIndex][cellIndex] = height;
                    candidatePresent[tileIndex][cellIndex] = true;
                }
            }
        }

        /** Promotes an unobserved requested mapchunk using legacy whole-mapchunk semantics. */
        public void promoteMissing(MapChunkCoordinate coordinate) {
            ensureMutable();
            int tileIndex = tileIndex(coordinate);
            if (tileIndex >= 0 && !accepted[tileIndex]) {
                promote(tileIndex);
            }
        }

        public SurfaceRainHeightPlan finish() {
            ensureMutable();
            finished = true;
            Map<ChunkPosition, LongArrayBuilder> byChunk = new TreeMap<>(CHUNK_ORDER);
            for (int tileIndex = 0; tileIndex < layout.tileCount(); tileIndex++) {
                if (promoted[tileIndex]) {
                    continue;
                }
                int tileX = layout.tileXAt(tileIndex);
                int tileZ = layout.tileZAt(tileIndex);
                int width = layout.tileWidth(tileX);
                int height = layout.tileHeight(tileZ);
                for (int localZ = 0; localZ < height; localZ++) {
                    for (int localX = 0; localX < width; localX++) {
                        int cellIndex = layout.cellIndex(tileX, tileZ, localX, localZ);
                        if (!candidatePresent[tileIndex][cellIndex]) {
                            continue;
                        }
                        int worldX = layout.worldXForTileLocal(tileX, localX);
                        int worldZ = layout.worldZForTileLocal(tileZ, localZ);
                        int worldY = rainHeights[tileIndex][cellIndex];
                        ChunkPosition position = new ChunkPosition(
                                Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS),
                                Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS),
                                Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS),
                                0
                        );
                        byChunk.computeIfAbsent(position, ignored -> new LongArrayBuilder())
                                .add(encode(tileIndex, cellIndex));
                    }
                }
            }
            List<ChunkPosition> positions = List.copyOf(byChunk.keySet());
            Map<ChunkPosition, long[]> immutableByChunk = new TreeMap<>(CHUNK_ORDER);
            for (Map.Entry<ChunkPosition, LongArrayBuilder> entry : byChunk.entrySet()) {
                immutableByChunk.put(entry.getKey(), entry.getValue().toArray());
            }
            List<MapChunkCoordinate> fallbacks = new ArrayList<>();
            for (int tileIndex = 0; tileIndex < promoted.length; tileIndex++) {
                if (promoted[tileIndex]) {
                    fallbacks.add(new MapChunkCoordinate(
                            layout.tileXAt(tileIndex), layout.tileZAt(tileIndex)));
                }
            }
            fallbacks.sort(MAPCHUNK_ORDER);
            return new SurfaceRainHeightPlan(
                    layout,
                    rainHeights,
                    candidatePresent,
                    promoted,
                    immutableByChunk,
                    positions,
                    fallbacks
            );
        }

        private int tileIndex(MapChunkCoordinate coordinate) {
            Objects.requireNonNull(coordinate, "mapchunk coordinate is required");
            try {
                return layout.tileIndex(coordinate.x(), coordinate.z());
            } catch (IndexOutOfBoundsException ignored) {
                return -1;
            }
        }

        private void promote(int tileIndex) {
            promoted[tileIndex] = true;
            Arrays.fill(candidatePresent[tileIndex], false);
        }

        private void ensureMutable() {
            if (finished) {
                throw new IllegalStateException("RainHeight planner is finished");
            }
        }

        private static long encode(int tileIndex, int cellIndex) {
            return ((long) tileIndex << 32) | (cellIndex & 0xFFFFFFFFL);
        }

        private static final class LongArrayBuilder {
            private long[] values = new long[16];
            private int size;

            private void add(long value) {
                if (size == values.length) {
                    values = Arrays.copyOf(values, Math.multiplyExact(values.length, 2));
                }
                values[size++] = value;
            }

            private long[] toArray() {
                return Arrays.copyOf(values, size);
            }
        }
    }
}
