package cartographer.render;

import cartographer.application.MapTileData;
import cartographer.application.MapTileDataRequirement;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldPosition;
import cartographer.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapTileRendererTest {

    @Test
    void terrainPixelsMatchLegacyRendererForEquivalentWorldBlocks() {
        MapChunk chunk = chunk(0, 0, 70);
        TerrainHeightTile terrain = TerrainHeightTile.from(chunk);
        RenderTileBounds bounds = new RenderTileBounds(0, 0, 32, 32);
        MapTileData data = data(
                bounds,
                List.of(terrain)
        );
        TerrainColorRange range = TerrainColorRange.fromTiles(
                List.of(terrain)
        );

        RenderedMapTile tile = new MapTileRenderer().renderTerrain(
                new RenderTileCoordinate(0, 0),
                data,
                Map.of(),
                range,
                RenderStyle.TOPOGRAPHIC
        );

        WorldPosition center = new WorldPosition(16.0, 0.0, 16.0);
        RenderOptions options = new RenderOptions(
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN)
        );
        RenderedMap legacy = new MapRenderer().render(
                center,
                HomeState.absent(),
                List.of(chunk),
                options,
                ProgressReporter.NONE
        );
        RenderSamplingPlan sampling = RenderSamplingPlan.from(
                center,
                options
        );

        for (int worldZ = 0; worldZ < 32; worldZ++) {
            int legacyY = imageCoordinateForWorldZ(
                    sampling,
                    worldZ
            );
            for (int worldX = 0; worldX < 32; worldX++) {
                int legacyX = imageCoordinateForWorldX(
                        sampling,
                        worldX
                );
                assertEquals(
                        legacy.image().getRGB(legacyX, legacyY),
                        tile.image().getRGB(worldX, worldZ),
                        "world block " + worldX + "," + worldZ
                );
            }
        }
    }

    @Test
    void sharedTerrainContextKeepsHillshadeContinuousAcrossTileBoundary() {
        MapChunk leftChunk = chunk(0, 0, 40);
        MapChunk rightChunk = chunk(1, 0, 120);
        TerrainHeightTile left = TerrainHeightTile.from(leftChunk);
        TerrainHeightTile right = TerrainHeightTile.from(rightChunk);
        TerrainColorRange range = TerrainColorRange.fromTiles(
                List.of(left, right)
        );
        Map<MapChunkCoordinate, TerrainHeightTile> context =
                Map.of(
                        left.coordinate(), left,
                        right.coordinate(), right
                );

        RenderedMapTile leftRendered = new MapTileRenderer().renderTerrain(
                new RenderTileCoordinate(0, 0),
                data(new RenderTileBounds(0, 0, 32, 32), List.of(left)),
                context,
                range,
                RenderStyle.SIMPLE
        );
        RenderedMapTile rightRendered = new MapTileRenderer().renderTerrain(
                new RenderTileCoordinate(1, 0),
                data(new RenderTileBounds(32, 0, 64, 32), List.of(right)),
                context,
                range,
                RenderStyle.SIMPLE
        );

        WorldPosition center = new WorldPosition(32.0, 0.0, 16.0);
        RenderOptions options = new RenderOptions(
                32,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN)
        );
        RenderedMap legacy = new MapRenderer().render(
                center,
                HomeState.absent(),
                List.of(leftChunk, rightChunk),
                options,
                ProgressReporter.NONE
        );
        RenderSamplingPlan sampling = RenderSamplingPlan.from(center, options);
        int legacyY = imageCoordinateForWorldZ(sampling, 16);

        assertEquals(
                legacy.image().getRGB(
                        imageCoordinateForWorldX(sampling, 31),
                        legacyY
                ),
                leftRendered.image().getRGB(31, 16)
        );
        assertEquals(
                legacy.image().getRGB(
                        imageCoordinateForWorldX(sampling, 32),
                        legacyY
                ),
                rightRendered.image().getRGB(0, 16)
        );
    }

    private MapTileData data(
            RenderTileBounds bounds,
            List<TerrainHeightTile> terrain
    ) {
        LinkedHashMap<MapChunkCoordinate, TerrainHeightTile> tiles =
                new LinkedHashMap<>();
        for (TerrainHeightTile tile : terrain) {
            tiles.put(tile.coordinate(), tile);
        }
        return new MapTileData(
                bounds,
                Optional.of(bounds),
                terrain.stream()
                        .map(TerrainHeightTile::coordinate)
                        .toList(),
                tiles,
                Map.of(),
                Map.of(),
                MapTileDataRequirement.TERRAIN
        );
    }

    private int imageCoordinateForWorldX(
            RenderSamplingPlan sampling,
            int worldX
    ) {
        for (int imageX = 0; imageX < sampling.rasterSize(); imageX++) {
            if (sampling.worldXForImageColumn(imageX) == worldX) {
                return imageX;
            }
        }
        throw new AssertionError("world X not sampled: " + worldX);
    }

    private int imageCoordinateForWorldZ(
            RenderSamplingPlan sampling,
            int worldZ
    ) {
        for (int imageY = 0; imageY < sampling.rasterSize(); imageY++) {
            if (sampling.worldZForImageRow(imageY) == worldZ) {
                return imageY;
            }
        }
        throw new AssertionError("world Z not sampled: " + worldZ);
    }

    private MapChunk chunk(
            int chunkX,
            int chunkZ,
            int baseHeight
    ) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
            for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                heights[localZ * MapChunk.SIZE + localX] =
                        baseHeight + localX + localZ;
            }
        }
        return new MapChunk(
                new MapChunkCoordinate(chunkX, chunkZ),
                heights,
                new int[0]
        );
    }
}
