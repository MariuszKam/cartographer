package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable primitive candidate plan for streaming Surface Object discovery. */
public final class SurfaceObjectCompactPlan {
    static final int MAX_CANDIDATES_PER_COLUMN = 10;

    private final List<Tile> tiles;
    private final List<ChunkPosition> chunkPositions;
    private final Map<MapChunkCoordinate, Integer> tileIndexes;
    private final int plannedTargetCount;

    SurfaceObjectCompactPlan(
            List<Tile> tiles,
            List<ChunkPosition> chunkPositions,
            Map<MapChunkCoordinate, Integer> tileIndexes,
            int plannedTargetCount
    ) {
        this.tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles are required"));
        this.chunkPositions = List.copyOf(Objects.requireNonNull(
                chunkPositions, "chunk positions are required"));
        this.tileIndexes = Map.copyOf(Objects.requireNonNull(
                tileIndexes, "tile indexes are required"));
        this.plannedTargetCount = plannedTargetCount;
        if (plannedTargetCount < 0) {
            throw new IllegalArgumentException("planned target count cannot be negative");
        }
    }

    public static SurfaceObjectCompactPlan empty() {
        return new SurfaceObjectCompactPlan(List.of(), List.of(), Map.of(), 0);
    }

    public List<ChunkPosition> chunkPositions() {
        return chunkPositions;
    }

    public int plannedTargetCount() {
        return plannedTargetCount;
    }

    public int tileCount() {
        return tiles.size();
    }

    Tile tileAt(int index) {
        return tiles.get(index);
    }

    int tileIndexAt(MapChunkCoordinate coordinate) {
        Integer index = tileIndexes.get(coordinate);
        return index == null ? -1 : index;
    }

    public static final class Tile {
        private final MapChunkCoordinate coordinate;
        private final int[] candidateYs;
        private final int[] candidateChunkIndexes;
        private final byte[] candidateCounts;

        Tile(
                MapChunkCoordinate coordinate,
                int[] candidateYs,
                int[] candidateChunkIndexes,
                byte[] candidateCounts
        ) {
            this.coordinate = Objects.requireNonNull(coordinate, "coordinate is required");
            // Ownership is transferred from the finished planner session. The
            // arrays are private and no mutable view is exposed.
            this.candidateYs = candidateYs;
            this.candidateChunkIndexes = candidateChunkIndexes;
            this.candidateCounts = candidateCounts;
            int cells = Math.multiplyExact(cartographer.model.MapChunk.SIZE,
                    cartographer.model.MapChunk.SIZE);
            int expected = Math.multiplyExact(cells, MAX_CANDIDATES_PER_COLUMN);
            if (this.candidateYs.length != expected
                    || this.candidateChunkIndexes.length != expected
                    || this.candidateCounts.length != cells) {
                throw new IllegalArgumentException("compact tile arrays have invalid lengths");
            }
        }

        public MapChunkCoordinate coordinate() {
            return coordinate;
        }

        int candidateCountAt(int cellIndex) {
            return Byte.toUnsignedInt(candidateCounts[cellIndex]);
        }

        int candidateYAt(int cellIndex, int candidateIndex) {
            return candidateYs[cellIndex * MAX_CANDIDATES_PER_COLUMN + candidateIndex];
        }

        int candidateChunkIndexAt(int cellIndex, int candidateIndex) {
            return candidateChunkIndexes[cellIndex * MAX_CANDIDATES_PER_COLUMN + candidateIndex];
        }

        byte[] newTargetState() {
            return new byte[candidateCounts.length];
        }
    }
}
