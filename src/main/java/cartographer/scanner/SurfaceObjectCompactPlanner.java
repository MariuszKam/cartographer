package cartographer.scanner;

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

/** Plans Surface Object candidates into compact mapchunk-sized tiles. */
public final class SurfaceObjectCompactPlanner {
    private static final Comparator<MapChunkCoordinate> TILE_ORDER = Comparator.comparingInt(MapChunkCoordinate::z).thenComparingInt(MapChunkCoordinate::x);
    private static final Comparator<ChunkPosition> CHUNK_ORDER = Comparator.comparingInt(ChunkPosition::y).thenComparingInt(ChunkPosition::z).thenComparingInt(ChunkPosition::x).thenComparingInt(ChunkPosition::dimension);

    public StreamingSession begin(WorldMetadata metadata, int centerWorldX, int centerWorldZ, int radius) {
        Objects.requireNonNull(metadata, "metadata is required");
        if (radius <= 0) throw new IllegalArgumentException("radius must be positive");
        return new StreamingSession(metadata, centerWorldX, centerWorldZ, Math.multiplyExact((long) radius, radius));
    }

    public static final class StreamingSession {
        private final WorldMetadata metadata;
        private final int centerWorldX;
        private final int centerWorldZ;
        private final long radiusSquared;
        private final Map<MapChunkCoordinate, TileBuilder> tiles = new TreeMap<>(TILE_ORDER);
        private boolean finished;

        private StreamingSession(WorldMetadata metadata, int centerWorldX, int centerWorldZ, long radiusSquared) {
            this.metadata = metadata;
            this.centerWorldX = centerWorldX;
            this.centerWorldZ = centerWorldZ;
            this.radiusSquared = radiusSquared;
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
            List<SurfaceObjectCompactPlan.Tile> compactTiles = new ArrayList<>(tiles.size());
            int plannedTargets = 0;
            for (TileBuilder tile : tiles.values()) {
                int cells = Math.multiplyExact(MapChunk.SIZE, MapChunk.SIZE);
                byte[] flags = new byte[cells];
                int[] terrain = tile.terrain == null ? new int[cells] : tile.terrain;
                int[] rain = tile.rain == null ? new int[cells] : tile.rain;
                for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                    for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                        int worldX = Math.addExact(Math.multiplyExact(tile.coordinate.x(), MapChunk.SIZE), localX);
                        int worldZ = Math.addExact(Math.multiplyExact(tile.coordinate.z(), MapChunk.SIZE), localZ);
                        if (!inWorld(worldX, worldZ) || !withinRadius(worldX, worldZ)) continue;
                        int cell = localZ * MapChunk.SIZE + localX;
                        byte sourceFlags = tile.sourceFlags();
                        if (sourceFlags == 0) continue;
                        int first = firstCandidateY(terrain[cell], rain[cell], sourceFlags);
                        int last = lastCandidateYExclusive(terrain[cell], rain[cell], sourceFlags);
                        if (first >= last) continue;
                        flags[cell] = sourceFlags;
                        plannedTargets = Math.addExact(plannedTargets, 1);
                        for (int worldY = first; worldY < last; worldY++) {
                            if (isCandidateY(terrain[cell], rain[cell], sourceFlags, worldY)) {
                                positions.add(
                                        Math.floorDiv(worldX, cartographer.model.ChunkCoordinate.SIZE_BLOCKS),
                                        Math.floorDiv(worldY, cartographer.model.ChunkCoordinate.SIZE_BLOCKS),
                                        Math.floorDiv(worldZ, cartographer.model.ChunkCoordinate.SIZE_BLOCKS));
                            }
                        }
                    }
                }
                compactTiles.add(new SurfaceObjectCompactPlan.Tile(tile.coordinate, metadata.mapSizeY(), terrain, rain, flags));
            }
            List<ChunkPosition> sortedPositions = positions.sortedPositions();
            Map<MapChunkCoordinate, Integer> tileIndexes = new TreeMap<>(TILE_ORDER);
            for (int index = 0; index < compactTiles.size(); index++) tileIndexes.put(compactTiles.get(index).coordinate(), index);
            return new SurfaceObjectCompactPlan(compactTiles, sortedPositions, tileIndexes, plannedTargets);
        }

        private int firstCandidateY(int terrain, int rain, byte flags) {
            long first = Long.MAX_VALUE;
            if ((flags & TileBuilder.TERRAIN_PRESENT) != 0) first = Math.min(first, (long) terrain - 2L);
            if ((flags & TileBuilder.RAIN_PRESENT) != 0) first = Math.min(first, rain);
            return first == Long.MAX_VALUE ? 0 : (int) Math.max(0L, first);
        }

        private int lastCandidateYExclusive(int terrain, int rain, byte flags) {
            long last = Long.MIN_VALUE;
            if ((flags & TileBuilder.TERRAIN_PRESENT) != 0) last = Math.max(last, (long) terrain + 4L);
            if ((flags & TileBuilder.RAIN_PRESENT) != 0) last = Math.max(last, (long) rain + 4L);
            return last == Long.MIN_VALUE ? 0 : (int) Math.min(metadata.mapSizeY(), last);
        }

        private boolean isCandidateY(int terrain, int rain, byte flags, int y) {
            return ((flags & TileBuilder.TERRAIN_PRESENT) != 0 && y >= (long) terrain - 2L && y < (long) terrain + 4L)
                    || ((flags & TileBuilder.RAIN_PRESENT) != 0 && y >= rain && y < (long) rain + 4L);
        }

        private boolean inWorld(int x, int z) { return x >= 0 && x < metadata.mapSizeX() && z >= 0 && z < metadata.mapSizeZ(); }

        private boolean withinRadius(int x, int z) {
            long dx = (long) x - centerWorldX;
            long dz = (long) z - centerWorldZ;
            return Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)) <= radiusSquared;
        }

        private void ensureMutable() { if (finished) throw new IllegalStateException("compact object planner is finished"); }
    }

    private record TileBuilder(
            MapChunkCoordinate coordinate,
            boolean hasTerrain,
            boolean hasRain,
            int[] terrain,
            int[] rain
    ) {
        private static final byte TERRAIN_PRESENT = 1;
        private static final byte RAIN_PRESENT = 1 << 1;

        static TileBuilder copyOf(MapChunk mapChunk) {
            return new TileBuilder(mapChunk.coordinate(), mapChunk.hasWorldGenTerrainHeightMap(), mapChunk.hasRainHeightMap(),
                    mapChunk.hasWorldGenTerrainHeightMap() ? Arrays.copyOf(mapChunk.worldGenTerrainHeightMap(), MapChunk.HEIGHT_VALUE_COUNT) : null,
                    mapChunk.hasRainHeightMap() ? Arrays.copyOf(mapChunk.rainHeightMap(), MapChunk.HEIGHT_VALUE_COUNT) : null);
        }

        byte sourceFlags() { return (byte) ((hasTerrain ? TERRAIN_PRESENT : 0) | (hasRain ? RAIN_PRESENT : 0)); }
    }

    private static final class ChunkPositionIndex {
        private int[] xs = new int[16], ys = new int[16], zs = new int[16], slots = new int[32];
        private int size;

        void add(int x, int y, int z) {
            if (size * 2 >= slots.length) grow();
            int slot = slot(x, y, z, slots.length);
            while (true) {
                int stored = slots[slot];
                if (stored == 0) {
                    int index = size++;
                    ensureValues(index + 1);
                    xs[index] = x; ys[index] = y; zs[index] = z; slots[slot] = index + 1;
                    return;
                }
                int index = stored - 1;
                if (xs[index] == x && ys[index] == y && zs[index] == z) return;
                slot = (slot + 1) & (slots.length - 1);
            }
        }

        List<ChunkPosition> sortedPositions() {
            List<ChunkPosition> result = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                result.add(new ChunkPosition(
                        xs[index],
                        ys[index],
                        zs[index],
                        0
                ));
            }
            result.sort(CHUNK_ORDER);
            return result;
        }

        private void grow() {
            int[] old = slots;
            slots = new int[old.length * 2];
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
