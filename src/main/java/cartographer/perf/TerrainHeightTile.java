package cartographer.perf;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapChunkHeightView;

import java.util.Arrays;
import java.util.Objects;

/** Compact immutable terrain data derived from one mapchunk. */
public final class TerrainHeightTile implements MapChunkHeightView {
    public static final int HEIGHT_VALUE_COUNT = MapChunk.HEIGHT_VALUE_COUNT;

    private final MapChunkCoordinate coordinate;
    private final boolean rainHeightAvailable;
    private final boolean effectiveHeightAvailable;
    private final int[] effectiveHeights;

    public TerrainHeightTile(
            MapChunkCoordinate coordinate,
            boolean rainHeightAvailable,
            boolean effectiveHeightAvailable,
            int[] effectiveHeights
    ) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate is required");
        if (rainHeightAvailable && !effectiveHeightAvailable) {
            throw new IllegalArgumentException(
                    "rain height availability requires effective heights"
            );
        }
        int[] values = Objects.requireNonNull(effectiveHeights, "effective heights are required");
        if (effectiveHeightAvailable && values.length != HEIGHT_VALUE_COUNT) {
            throw new IllegalArgumentException(
                    "effective heights must contain " + HEIGHT_VALUE_COUNT + " values"
            );
        }
        if (!effectiveHeightAvailable && values.length != 0) {
            throw new IllegalArgumentException(
                    "unavailable effective heights must be empty"
            );
        }
        this.rainHeightAvailable = rainHeightAvailable;
        this.effectiveHeightAvailable = effectiveHeightAvailable;
        this.effectiveHeights = Arrays.copyOf(values, values.length);
    }

    public static TerrainHeightTile from(MapChunk mapChunk) {
        Objects.requireNonNull(mapChunk, "map chunk is required");
        if (mapChunk.hasRainHeightMap()) {
            return new TerrainHeightTile(
                    mapChunk.coordinate(), true, true, mapChunk.rainHeightMap()
            );
        }
        if (mapChunk.hasWorldGenTerrainHeightMap()) {
            return new TerrainHeightTile(
                    mapChunk.coordinate(), false, true,
                    mapChunk.worldGenTerrainHeightMap()
            );
        }
        return new TerrainHeightTile(
                mapChunk.coordinate(), false, false, new int[0]
        );
    }

    public MapChunkCoordinate coordinate() {
        return coordinate;
    }

    public boolean rainHeightAvailable() {
        return rainHeightAvailable;
    }

    @Override
    public boolean hasRainHeight() {
        return rainHeightAvailable;
    }

    @Override
    public boolean hasEffectiveHeight() {
        return effectiveHeightAvailable;
    }

    public boolean effectiveHeightAvailable() {
        return effectiveHeightAvailable;
    }

    public int[] effectiveHeights() {
        return Arrays.copyOf(effectiveHeights, effectiveHeights.length);
    }

    public int effectiveHeightAt(int localX, int localZ) {
        if (!effectiveHeightAvailable) {
            throw new IllegalStateException("effective heights are unavailable");
        }
        if (localX < 0 || localX >= MapChunk.SIZE || localZ < 0 || localZ >= MapChunk.SIZE) {
            throw new IllegalArgumentException("local mapchunk coordinate out of bounds");
        }
        return effectiveHeights[localZ * MapChunk.SIZE + localX];
    }

    @Override
    public int rainHeightAt(int localX, int localZ) {
        if (!rainHeightAvailable) {
            throw new IllegalStateException("RainHeightMap is unavailable");
        }
        return effectiveHeightAt(localX, localZ);
    }
}
