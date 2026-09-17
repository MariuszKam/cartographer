package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Plans Surface Object candidates into primitive mapchunk-sized tiles. */
public final class SurfaceObjectCompactPlanner {
    private static final Comparator<MapChunkCoordinate> TILE_ORDER =
            Comparator.comparingInt(MapChunkCoordinate::z)
                    .thenComparingInt(MapChunkCoordinate::x);
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
        private final Map<MapChunkCoordinate, TileBuilder> tiles = new TreeMap<>(TILE_ORDER);
        private boolean finished;

        private StreamingSession(
                WorldMetadata metadata,
                int centerWorldX,
                int centerWorldZ,
                int radius
        ) {
            this.metadata = metadata;
            this.centerWorldX = centerWorldX;
            this.centerWorldZ = centerWorldZ;
            this.radiusSquared = Math.multiplyExact((long) radius, radius);
        }

        /** Consumes mapchunk height data immediately; no MapChunk is retained. */
        public void accept(MapChunk mapChunk) {
            ensureMutable();
            Objects.requireNonNull(mapChunk, "mapchunk is required");
            tiles.putIfAbsent(mapChunk.coordinate(), TileBuilder.copyOf(mapChunk));
        }

        public SurfaceObjectCompactPlan finish() {
            ensureMutable();
            finished = true;
            ChunkPositionIndex positions = new ChunkPositionIndex();
            int plannedTargets = 0;
            for (TileBuilder tile : tiles.values()) {
                int[] candidateYs = new int[MapChunk.SIZE * MapChunk.SIZE
                        * SurfaceObjectCompactPlan.MAX_CANDIDATES_PER_COLUMN];
                int[] candidateChunkIndexes = new int[candidateYs.length];
                byte[] candidateCounts = new byte[MapChunk.SIZE * MapChunk.SIZE];
                for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                    for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                        int worldX = Math.addExact(
                                Math.multiplyExact(tile.coordinate.x(), MapChunk.SIZE), localX);
                        int worldZ = Math.addExact(
                                Math.multiplyExact(tile.coordinate.z(), MapChunk.SIZE), localZ);
                        if (!inWorld(worldX, worldZ) || !withinRadius(worldX, worldZ)) {
                            continue;
                        }
                        int cell = localZ * MapChunk.SIZE + localX;
                        int count = writeCandidates(tile, localX, localZ,
                                worldX, worldZ, candidateYs, candidateChunkIndexes,
                                cell, positions);
                        candidateCounts[cell] = (byte) count;
                        if (count != 0) {
                            plannedTargets = Math.addExact(plannedTargets, 1);
                        }
                    }
                }
                tile.candidateYs = candidateYs;
                tile.candidateChunkIndexes = candidateChunkIndexes;
                tile.candidateCounts = candidateCounts;
            }

            List<Integer> order = new ArrayList<>(positions.size());
            for (int index = 0; index < positions.size(); index++) {
                order.add(index);
            }
            order.sort((left, right) -> CHUNK_ORDER.compare(
                    positions.positionAt(left), positions.positionAt(right)));
            int[] remap = new int[positions.size()];
            List<ChunkPosition> sortedPositions = new ArrayList<>(positions.size());
            for (int sorted = 0; sorted < order.size(); sorted++) {
                int original = order.get(sorted);
                remap[original] = sorted;
                sortedPositions.add(positions.positionAt(original));
            }
            List<SurfaceObjectCompactPlan.Tile> remappedTiles = new ArrayList<>(tiles.size());
            for (TileBuilder tile : tiles.values()) {
                remappedTiles.add(tile.toCompactTile(remap));
            }
            Map<MapChunkCoordinate, Integer> tileIndexes = new TreeMap<>(TILE_ORDER);
            for (int index = 0; index < remappedTiles.size(); index++) {
                tileIndexes.put(remappedTiles.get(index).coordinate(), index);
            }
            return new SurfaceObjectCompactPlan(
                    remappedTiles, sortedPositions, tileIndexes, plannedTargets);
        }

        private int writeCandidates(
                TileBuilder tile,
                int localX,
                int localZ,
                int worldX,
                int worldZ,
                int[] candidateYs,
                int[] candidateChunkIndexes,
                int cell,
                ChunkPositionIndex positions
        ) {
            long terrain = tile.hasTerrain ? tile.terrain[cell] : Long.MIN_VALUE;
            long rain = tile.hasRain ? tile.rain[cell] : Long.MIN_VALUE;
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            if (tile.hasTerrain) {
                min = Math.min(min, terrain - 2L);
                max = Math.max(max, terrain + 4L);
            }
            if (tile.hasRain) {
                min = Math.min(min, rain);
                max = Math.max(max, rain + 4L);
            }
            if (min == Long.MAX_VALUE) {
                return 0;
            }
            int first = (int) Math.max(0L, min);
            int lastExclusive = (int) Math.min((long) metadata.mapSizeY(), max);
            if (first >= lastExclusive) {
                return 0;
            }
            int count = 0;
            for (int worldY = first; worldY < lastExclusive; worldY++) {
                boolean inTerrain = tile.hasTerrain
                        && worldY >= terrain - 2L && worldY < terrain + 4L;
                boolean inRain = tile.hasRain
                        && worldY >= rain && worldY < rain + 4L;
                if (!inTerrain && !inRain) {
                    continue;
                }
                if (count >= SurfaceObjectCompactPlan.MAX_CANDIDATES_PER_COLUMN) {
                    throw new IllegalStateException("candidate range exceeds compact capacity");
                }
                int offset = cell * SurfaceObjectCompactPlan.MAX_CANDIDATES_PER_COLUMN + count;
                candidateYs[offset] = worldY;
                candidateChunkIndexes[offset] = positions.indexOf(
                        Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS),
                        Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS),
                        Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS));
                count++;
            }
            return count;
        }

        private boolean inWorld(int worldX, int worldZ) {
            return worldX >= 0 && worldX < metadata.mapSizeX()
                    && worldZ >= 0 && worldZ < metadata.mapSizeZ();
        }

        private boolean withinRadius(int worldX, int worldZ) {
            long dx = (long) worldX - centerWorldX;
            long dz = (long) worldZ - centerWorldZ;
            return Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz))
                    <= radiusSquared;
        }

        private void ensureMutable() {
            if (finished) {
                throw new IllegalStateException("compact object planner is finished");
            }
        }
    }

    private static final class TileBuilder {
        private final MapChunkCoordinate coordinate;
        private final boolean hasTerrain;
        private final boolean hasRain;
        private final int[] terrain;
        private final int[] rain;
        private int[] candidateYs;
        private int[] candidateChunkIndexes;
        private byte[] candidateCounts;

        private TileBuilder(
                MapChunkCoordinate coordinate,
                boolean hasTerrain,
                boolean hasRain,
                int[] terrain,
                int[] rain
        ) {
            this.coordinate = coordinate;
            this.hasTerrain = hasTerrain;
            this.hasRain = hasRain;
            this.terrain = terrain;
            this.rain = rain;
        }

        static TileBuilder copyOf(MapChunk mapChunk) {
            return new TileBuilder(
                    mapChunk.coordinate(),
                    mapChunk.hasWorldGenTerrainHeightMap(),
                    mapChunk.hasRainHeightMap(),
                    mapChunk.hasWorldGenTerrainHeightMap()
                            ? Arrays.copyOf(mapChunk.worldGenTerrainHeightMap(),
                            MapChunk.HEIGHT_VALUE_COUNT) : null,
                    mapChunk.hasRainHeightMap()
                            ? Arrays.copyOf(mapChunk.rainHeightMap(),
                            MapChunk.HEIGHT_VALUE_COUNT) : null
            );
        }

        SurfaceObjectCompactPlan.Tile toCompactTile(int[] remap) {
            int[] chunks = candidateChunkIndexes;
            for (int cell = 0; cell < candidateCounts.length; cell++) {
                int count = Byte.toUnsignedInt(candidateCounts[cell]);
                for (int candidate = 0; candidate < count; candidate++) {
                    int index = cell * SurfaceObjectCompactPlan.MAX_CANDIDATES_PER_COLUMN + candidate;
                    chunks[index] = remap[chunks[index]];
                }
            }
            return new SurfaceObjectCompactPlan.Tile(
                    coordinate, candidateYs, chunks, candidateCounts);
        }
    }

    private static final class ChunkPositionIndex {
        private int[] xs = new int[16];
        private int[] ys = new int[16];
        private int[] zs = new int[16];
        private int[] slots = new int[32];
        private int size;

        int size() { return size; }

        ChunkPosition positionAt(int index) {
            return new ChunkPosition(xs[index], ys[index], zs[index], 0);
        }

        int indexOf(int x, int y, int z) {
            if (size * 2 >= slots.length) grow();
            int slot = slot(x, y, z, slots.length);
            while (true) {
                int stored = slots[slot];
                if (stored == 0) {
                    int index = size++;
                    ensureValues(index + 1);
                    xs[index] = x; ys[index] = y; zs[index] = z;
                    slots[slot] = index + 1;
                    return index;
                }
                int index = stored - 1;
                if (xs[index] == x && ys[index] == y && zs[index] == z) return index;
                slot = (slot + 1) & (slots.length - 1);
            }
        }

        private void grow() {
            int[] oldSlots = slots;
            slots = new int[oldSlots.length * 2];
            for (int index = 0; index < size; index++) {
                int slot = slot(xs[index], ys[index], zs[index], slots.length);
                while (slots[slot] != 0) slot = (slot + 1) & (slots.length - 1);
                slots[slot] = index + 1;
            }
        }

        private void ensureValues(int required) {
            if (required <= xs.length) return;
            int length = xs.length * 2;
            xs = Arrays.copyOf(xs, length); ys = Arrays.copyOf(ys, length); zs = Arrays.copyOf(zs, length);
        }

        private int slot(int x, int y, int z, int length) {
            long hash = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
            hash ^= hash >>> 33;
            return (int) hash & (length - 1);
        }
    }
}
