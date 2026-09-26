package cartographer.scanner;

import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compact, immutable output of tile-based RainHeight planning. */
public final class SurfaceRainHeightPlan {
    private final SurfaceTileLayout layout;
    private final int[][] rainHeights;
    private final boolean[][] candidatePresent;
    private final boolean[] promotedMapChunks;
    private final Map<ChunkPosition, long[]> cellOrdinalsByChunk;
    private final List<ChunkPosition> chunkPositions;
    private final List<MapChunkCoordinate> fallbackMapChunks;

    SurfaceRainHeightPlan(
            SurfaceTileLayout layout,
            int[][] rainHeights,
            boolean[][] candidatePresent,
            boolean[] promotedMapChunks,
            Map<ChunkPosition, long[]> cellOrdinalsByChunk,
            List<ChunkPosition> chunkPositions,
            List<MapChunkCoordinate> fallbackMapChunks
    ) {
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.rainHeights = copyRainHeights(rainHeights);
        this.candidatePresent = copyCandidatePresence(candidatePresent);
        this.promotedMapChunks = Arrays.copyOf(
                Objects.requireNonNull(promotedMapChunks, "promoted mapchunks are required"),
                promotedMapChunks.length
        );
        this.cellOrdinalsByChunk = Map.copyOf(
                Objects.requireNonNull(cellOrdinalsByChunk, "cell ordinals are required")
        );
        this.chunkPositions = List.copyOf(Objects.requireNonNull(chunkPositions, "chunk positions are required"));
        this.fallbackMapChunks = List.copyOf(
                Objects.requireNonNull(fallbackMapChunks, "fallback mapchunks are required")
        );
        if (this.rainHeights.length != layout.tileCount()
                || this.candidatePresent.length != layout.tileCount()
                || this.promotedMapChunks.length != layout.tileCount()) {
            throw new IllegalArgumentException("plan arrays do not match layout");
        }
    }

    public SurfaceTileLayout layout() {
        return layout;
    }

    public List<ChunkPosition> chunkPositions() {
        return chunkPositions;
    }

    public List<MapChunkCoordinate> fallbackMapChunks() {
        return fallbackMapChunks;
    }

    public int tileCount() {
        return layout.tileCount();
    }

    public boolean isPromoted(int tileIndex) {
        return promotedMapChunks[tileIndex];
    }

    int rainHeightAt(int tileIndex, int cellIndex) {
        return rainHeights[tileIndex][cellIndex];
    }

    boolean hasCandidate(int tileIndex, int cellIndex) {
        return candidatePresent[tileIndex][cellIndex];
    }

    long[] cellOrdinalsFor(ChunkPosition position) {
        return cellOrdinalsByChunk.get(position);
    }

    private static int[][] copyRainHeights(int[][] values) {
        Objects.requireNonNull(values, "rain heights are required");
        int[][] result = new int[values.length][];
        for (int index = 0; index < values.length; index++) {
            result[index] = Arrays.copyOf(
                    Objects.requireNonNull(
                            values[index],
                            "rain heights cannot contain null"
                    ),
                    values[index].length
            );
        }
        return result;
    }

    private static boolean[][] copyCandidatePresence(boolean[][] values) {
        Objects.requireNonNull(values, "candidate presence is required");
        boolean[][] result = new boolean[values.length][];
        for (int index = 0; index < values.length; index++) {
            result[index] = Arrays.copyOf(
                    Objects.requireNonNull(
                            values[index],
                            "candidate presence cannot contain null"
                    ),
                    values[index].length
            );
        }
        return result;
    }
}
