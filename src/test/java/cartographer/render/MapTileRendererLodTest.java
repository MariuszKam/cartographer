package cartographer.render;

import cartographer.application.MapTileData;
import cartographer.application.MapTileDataRequirement;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapTileRendererLodTest {

    @Test
    void coarseLodReducesRasterSizeWithoutChangingWorldBounds() {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, 100);
        TerrainHeightTile terrain = TerrainHeightTile.from(
                new MapChunk(coordinate, heights, new int[0])
        );
        RenderTileBounds bounds = new RenderTileBounds(0, 0, 32, 32);
        MapTileData data = new MapTileData(
                bounds,
                Optional.of(bounds),
                List.of(coordinate),
                Map.of(coordinate, terrain),
                Map.of(),
                Map.of(),
                MapTileDataRequirement.TERRAIN
        );

        RenderedMapTile rendered = new MapTileRenderer().render(
                new RenderTileCoordinate(0, 0),
                data,
                data.terrainTiles(),
                new TerrainColorRange(0, 255),
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Map.of(),
                RenderLod.LOD_2
        );

        assertEquals(bounds, rendered.worldBounds());
        assertEquals(RenderLod.LOD_2, rendered.lod());
        assertEquals(4, rendered.blocksPerPixel());
        assertEquals(8, rendered.image().getWidth());
        assertEquals(8, rendered.image().getHeight());
        assertEquals(64, rendered.terrainPixelsDrawn());
    }
}
