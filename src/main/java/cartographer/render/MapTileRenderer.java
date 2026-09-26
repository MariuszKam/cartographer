package cartographer.render;

import cartographer.application.MapTileData;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Renders one independent progressive base-map tile.
 *
 * <p>Phase 5 renders one world block per output pixel. LOD changes this later;
 * no global radius or {@link MapRasterContract} participates in this path.</p>
 */
public final class MapTileRenderer {
    private final TerrainPalette palette = new TerrainPalette();

    public RenderedMapTile renderTerrain(
            RenderTileCoordinate coordinate,
            MapTileData data,
            Map<MapChunkCoordinate, TerrainHeightTile> terrainContext,
            TerrainColorRange colorRange,
            RenderStyle style
    ) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(data, "data is required");
        Objects.requireNonNull(terrainContext, "terrainContext is required");
        Objects.requireNonNull(colorRange, "colorRange is required");
        Objects.requireNonNull(style, "style is required");

        RenderTileBounds bounds = data.effectiveWorldBounds()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "cannot render a tile outside world bounds"
                        )
                );
        int width = Math.toIntExact(bounds.widthBlocks());
        int height = Math.toIntExact(bounds.heightBlocks());
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );
        ArgbRaster raster = ArgbRaster.wrap(image);

        int background = palette.background(style);
        for (int imageY = 0; imageY < height; imageY++) {
            raster.fillRow(imageY, background);
        }

        LinkedHashMap<MapChunkCoordinate, TerrainHeightTile> samples =
                new LinkedHashMap<>(terrainContext);
        samples.putAll(data.terrainTiles());

        int drawn = 0;
        for (int imageY = 0; imageY < height; imageY++) {
            int worldZ = Math.toIntExact(
                    bounds.worldMinZ() + imageY
            );
            for (int imageX = 0; imageX < width; imageX++) {
                int worldX = Math.toIntExact(
                        bounds.worldMinX() + imageX
                );
                TerrainSample sample = sampleAt(
                        samples,
                        worldX,
                        worldZ
                );
                if (sample == null) {
                    continue;
                }

                raster.setArgb(
                        imageX,
                        imageY,
                        palette.terrainColor(
                                sample.height(),
                                colorRange.minHeight(),
                                colorRange.maxHeight(),
                                hillshade(samples, worldX, worldZ),
                                style
                        )
                );
                drawn++;
            }
        }

        return new RenderedMapTile(
                coordinate,
                bounds,
                image,
                1,
                drawn,
                style
        );
    }

    private double hillshade(
            Map<MapChunkCoordinate, TerrainHeightTile> samples,
            int worldX,
            int worldZ
    ) {
        TerrainSample west = sampleAt(samples, worldX - 1, worldZ);
        TerrainSample east = sampleAt(samples, worldX + 1, worldZ);
        TerrainSample north = sampleAt(samples, worldX, worldZ - 1);
        TerrainSample south = sampleAt(samples, worldX, worldZ + 1);
        if (west == null
                || east == null
                || north == null
                || south == null) {
            return 0.0;
        }

        double dx = east.height() - west.height();
        double dz = south.height() - north.height();
        double light = (-dx * 0.55 - dz * 0.75) / 32.0;
        return Math.clamp(light, -0.35, 0.35);
    }

    private TerrainSample sampleAt(
            Map<MapChunkCoordinate, TerrainHeightTile> samples,
            int worldX,
            int worldZ
    ) {
        MapChunkCoordinate coordinate = MapChunkCoordinate.fromWorld(
                worldX,
                worldZ
        );
        TerrainHeightTile tile = samples.get(coordinate);
        if (tile == null || !tile.hasEffectiveHeight()) {
            return null;
        }

        int localX = Math.floorMod(worldX, MapChunk.SIZE);
        int localZ = Math.floorMod(worldZ, MapChunk.SIZE);
        return new TerrainSample(tile.effectiveHeightAt(localX, localZ));
    }

    private record TerrainSample(int height) {
    }
}
