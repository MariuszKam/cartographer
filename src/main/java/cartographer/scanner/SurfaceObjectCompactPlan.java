package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable candidate plan using two height anchors per tile cell. */
public final class SurfaceObjectCompactPlan {
    private final List<Tile> tiles;
    private final List<ChunkPosition> chunkPositions;
    private final Map<MapChunkCoordinate, Integer> tileIndexes;
    private final int plannedTargetCount;

    SurfaceObjectCompactPlan(List<Tile> tiles, List<ChunkPosition> chunkPositions,
                             Map<MapChunkCoordinate, Integer> tileIndexes, int plannedTargetCount) {
        this.tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles are required"));
        this.chunkPositions = List.copyOf(Objects.requireNonNull(chunkPositions, "chunk positions are required"));
        this.tileIndexes = Map.copyOf(Objects.requireNonNull(tileIndexes, "tile indexes are required"));
        if (plannedTargetCount < 0) throw new IllegalArgumentException("planned target count cannot be negative");
        this.plannedTargetCount = plannedTargetCount;
    }

    public static SurfaceObjectCompactPlan empty() {
        return new SurfaceObjectCompactPlan(List.of(), List.of(), Map.of(), 0);
    }

    public List<ChunkPosition> chunkPositions() { return chunkPositions; }
    public int plannedTargetCount() { return plannedTargetCount; }
    public int tileCount() { return tiles.size(); }
    Tile tileAt(int index) { return tiles.get(index); }

    int tileIndexAt(MapChunkCoordinate coordinate) {
        Integer index = tileIndexes.get(coordinate);
        return index == null ? -1 : index;
    }

    /** Finds a planned server chunk without allocating a coordinate lookup key. */
    int chunkPositionIndexAt(int worldX, int worldY, int worldZ) {
        int x = Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS);
        int y = Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS);
        int z = Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS);
        int low = 0;
        int high = chunkPositions.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            ChunkPosition position = chunkPositions.get(middle);
            int comparison = Integer.compare(position.y(), y);
            if (comparison == 0) comparison = Integer.compare(position.z(), z);
            if (comparison == 0) comparison = Integer.compare(position.x(), x);
            if (comparison < 0) low = middle + 1;
            else if (comparison > 0) high = middle - 1;
            else return middle;
        }
        return -1;
    }

    public static final class Tile {
        private static final byte TERRAIN_PRESENT = 1;
        private static final byte RAIN_PRESENT = 1 << 1;
        private final MapChunkCoordinate coordinate;
        private final int mapSizeY;
        private final int[] terrainAnchors;
        private final int[] rainAnchors;
        private final byte[] sourceFlags;

        Tile(MapChunkCoordinate coordinate, int mapSizeY, int[] terrainAnchors,
             int[] rainAnchors, byte[] sourceFlags) {
            this.coordinate = Objects.requireNonNull(coordinate, "coordinate is required");
            if (mapSizeY < 0) throw new IllegalArgumentException("map height cannot be negative");
            this.mapSizeY = mapSizeY;
            this.terrainAnchors = Objects.requireNonNull(terrainAnchors, "terrain anchors are required");
            this.rainAnchors = Objects.requireNonNull(rainAnchors, "rain anchors are required");
            this.sourceFlags = Objects.requireNonNull(sourceFlags, "source flags are required");
            int cells = Math.multiplyExact(MapChunk.SIZE, MapChunk.SIZE);
            if (terrainAnchors.length != cells || rainAnchors.length != cells || sourceFlags.length != cells) {
                throw new IllegalArgumentException("compact tile arrays have invalid lengths");
            }
        }

        public MapChunkCoordinate coordinate() { return coordinate; }

        int candidateCountAt(int cellIndex) {
            int count = 0;
            for (int y = firstCandidateY(cellIndex); y < lastCandidateYExclusive(cellIndex); y++) {
                if (isCandidateY(cellIndex, y)) count++;
            }
            return count;
        }

        int candidateYAt(int cellIndex, int candidateIndex) {
            if (candidateIndex < 0) throw new IndexOutOfBoundsException("candidate index");
            int found = 0;
            for (int y = firstCandidateY(cellIndex); y < lastCandidateYExclusive(cellIndex); y++) {
                if (isCandidateY(cellIndex, y) && found++ == candidateIndex) return y;
            }
            throw new IndexOutOfBoundsException("candidate index");
        }

        int firstCandidateY(int cellIndex) {
            long first = Long.MAX_VALUE;
            if ((sourceFlags[cellIndex] & TERRAIN_PRESENT) != 0) first = Math.min(first, (long) terrainAnchors[cellIndex] - 2L);
            if ((sourceFlags[cellIndex] & RAIN_PRESENT) != 0) first = Math.min(first, (long) rainAnchors[cellIndex]);
            return first == Long.MAX_VALUE ? 0 : (int) Math.max(0L, first);
        }

        int lastCandidateYExclusive(int cellIndex) {
            long last = Long.MIN_VALUE;
            if ((sourceFlags[cellIndex] & TERRAIN_PRESENT) != 0) last = Math.max(last, (long) terrainAnchors[cellIndex] + 4L);
            if ((sourceFlags[cellIndex] & RAIN_PRESENT) != 0) last = Math.max(last, (long) rainAnchors[cellIndex] + 4L);
            return last == Long.MIN_VALUE ? 0 : (int) Math.min((long) mapSizeY, last);
        }

        boolean isCandidateY(int cellIndex, int worldY) {
            boolean terrain = (sourceFlags[cellIndex] & TERRAIN_PRESENT) != 0
                    && worldY >= (long) terrainAnchors[cellIndex] - 2L
                    && worldY < (long) terrainAnchors[cellIndex] + 4L;
            boolean rain = (sourceFlags[cellIndex] & RAIN_PRESENT) != 0
                    && worldY >= rainAnchors[cellIndex]
                    && worldY < (long) rainAnchors[cellIndex] + 4L;
            return terrain || rain;
        }

        byte[] newTargetState() { return new byte[sourceFlags.length]; }
    }
}
