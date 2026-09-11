package cartographer.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record MapChunk(
        MapChunkCoordinate coordinate,
        int[] rainHeightMap,
        int[] worldGenTerrainHeightMap
) {
    public static final int SIZE = 32;
    public static final int HEIGHT_VALUE_COUNT = SIZE * SIZE;

    public MapChunk {
        rainHeightMap =
                copyHeightMap(
                        rainHeightMap
                );

        worldGenTerrainHeightMap =
                copyHeightMap(
                        worldGenTerrainHeightMap
                );
    }

    public boolean hasRainHeightMap() {
        return rainHeightMap.length == HEIGHT_VALUE_COUNT;
    }

    public boolean hasWorldGenTerrainHeightMap() {
        return worldGenTerrainHeightMap.length == HEIGHT_VALUE_COUNT;
    }

    public int[] rainHeightMap() {
        return Arrays.copyOf(
                rainHeightMap,
                rainHeightMap.length
        );
    }

    public int[] worldGenTerrainHeightMap() {
        return Arrays.copyOf(
                worldGenTerrainHeightMap,
                worldGenTerrainHeightMap.length
        );
    }

    public int heightAt(
            int localX,
            int localZ
    ) {
        validateLocalCoordinate(
                localX,
                localZ
        );

        int[] heights =
                hasRainHeightMap()
                        ? rainHeightMap
                        : worldGenTerrainHeightMap;

        return heights[index(
                localX,
                localZ
        )];
    }

    public List<MapTile> tiles() {
        int[] heights =
                hasRainHeightMap()
                        ? rainHeightMap
                        : worldGenTerrainHeightMap;

        if (heights.length == 0) {
            return List.of();
        }

        List<MapTile> tiles =
                new ArrayList<>(
                        HEIGHT_VALUE_COUNT
                );

        int originX =
                coordinate.x()
                        * SIZE;

        int originZ =
                coordinate.z()
                        * SIZE;

        for (int localZ = 0; localZ < SIZE; localZ++) {
            for (int localX = 0; localX < SIZE; localX++) {
                tiles.add(
                        new MapTile(
                                originX + localX,
                                originZ + localZ,
                                heights[index(
                                        localX,
                                        localZ
                                )]
                        )
                );
            }
        }

        return tiles;
    }

    private static int[] copyHeightMap(
            int[] values
    ) {
        if (values == null || values.length == 0) {
            return new int[0];
        }

        if (values.length != HEIGHT_VALUE_COUNT) {
            throw new IllegalArgumentException(
                    "height map must contain "
                            + HEIGHT_VALUE_COUNT
                            + " values"
            );
        }

        return Arrays.copyOf(
                values,
                values.length
        );
    }

    private static void validateLocalCoordinate(
            int localX,
            int localZ
    ) {
        if (localX < 0
                || localX >= SIZE
                || localZ < 0
                || localZ >= SIZE) {
            throw new IllegalArgumentException(
                    "local mapchunk coordinate out of bounds: "
                            + localX
                            + ","
                            + localZ
            );
        }
    }

    private static int index(
            int localX,
            int localZ
    ) {
        return localZ * SIZE
                + localX;
    }
}
