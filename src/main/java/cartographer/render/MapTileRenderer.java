package cartographer.render;

import cartographer.application.MapTileData;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.scanner.SurfaceRegistryLookup;
import cartographer.soil.SoilFertilityClassification;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renders one independent progressive base-map tile.
 *
 * <p>Phase 10 supports Terrain, semantic Surface and soil-fertility raster
 * layers. Markers are intentionally kept out of this raster and are drawn by
 * the viewport as vector overlays.</p>
 */
public final class MapTileRenderer {
    private final TerrainPalette palette = new TerrainPalette();
    private final SemanticTerrainPalette semanticPalette =
            new SemanticTerrainPalette();
    private final SoilFertilityPalette soilPalette =
            new SoilFertilityPalette();

    public RenderedMapTile renderTerrain(
            RenderTileCoordinate coordinate,
            MapTileData data,
            Map<MapChunkCoordinate, TerrainHeightTile> terrainContext,
            TerrainColorRange colorRange,
            RenderStyle style
    ) {
        return render(
                coordinate,
                data,
                terrainContext,
                colorRange,
                style,
                Set.of(RenderLayer.TERRAIN),
                Map.of(),
                RenderLod.fullDetail()
        );
    }

    public RenderedMapTile render(
            RenderTileCoordinate coordinate,
            MapTileData data,
            Map<MapChunkCoordinate, TerrainHeightTile> terrainContext,
            TerrainColorRange colorRange,
            RenderStyle style,
            Set<RenderLayer> layers,
            Map<Integer, BlockInfo> registry
    ) {

        return render(
                coordinate,
                data,
                terrainContext,
                colorRange,
                style,
                layers,
                registry,
                RenderLod.fullDetail()
        );
    }

    public RenderedMapTile render(
            RenderTileCoordinate coordinate,
            MapTileData data,
            Map<MapChunkCoordinate, TerrainHeightTile> terrainContext,
            TerrainColorRange colorRange,
            RenderStyle style,
            Set<RenderLayer> layers,
            Map<Integer, BlockInfo> registry,
            RenderLod lod
    ) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(data, "data is required");
        Objects.requireNonNull(terrainContext, "terrainContext is required");
        Objects.requireNonNull(colorRange, "colorRange is required");
        Objects.requireNonNull(style, "style is required");
        Objects.requireNonNull(layers, "layers are required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(lod, "lod is required");

        RenderTileBounds bounds = data.effectiveWorldBounds()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "cannot render a tile outside world bounds"
                        )
                );
        int blocksPerPixel = lod.blocksPerPixel();
        int width = Math.toIntExact(
                divideCeil(bounds.widthBlocks(), blocksPerPixel)
        );
        int height = Math.toIntExact(
                divideCeil(bounds.heightBlocks(), blocksPerPixel)
        );
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

        boolean drawTerrain = layers.contains(RenderLayer.TERRAIN);
        boolean drawSurface = layers.contains(RenderLayer.SURFACE);
        boolean drawSoil = layers.contains(RenderLayer.SOIL_FERTILITY);
        SurfaceRegistryLookup registryLookup = drawSoil
                ? new SurfaceRegistryLookup(registry)
                : null;

        int drawn = 0;
        for (int imageY = 0; imageY < height; imageY++) {
            int worldZ = sampleCoordinate(
                    bounds.worldMinZ(),
                    bounds.worldMaxZExclusive(),
                    imageY,
                    blocksPerPixel
            );
            for (int imageX = 0; imageX < width; imageX++) {
                int worldX = sampleCoordinate(
                        bounds.worldMinX(),
                        bounds.worldMaxXExclusive(),
                        imageX,
                        blocksPerPixel
                );
                TerrainSample terrain = sampleAt(
                        samples,
                        worldX,
                        worldZ
                );
                double shade = hillshade(
                        samples,
                        worldX,
                        worldZ,
                        blocksPerPixel
                );

                if (drawTerrain && terrain != null) {
                    raster.setArgb(
                            imageX,
                            imageY,
                            palette.terrainColor(
                                    terrain.height(),
                                    colorRange.minHeight(),
                                    colorRange.maxHeight(),
                                    shade,
                                    style
                            )
                    );
                    drawn++;
                }

                SurfaceSample surface = surfaceAt(
                        data,
                        worldX,
                        worldZ
                );
                if (drawSurface && surface != null) {
                    raster.setArgb(
                            imageX,
                            imageY,
                            semanticPalette.color(
                                    surface.surfaceClass(),
                                    shade
                            )
                    );
                }

                if (drawSoil
                        && surface != null
                        && surface.surfaceClass() != SurfaceClass.WATER
                        && surface.surfaceClass() != SurfaceClass.SNOW) {
                    SoilFertilityClassification fertility =
                            registryLookup.fertility(surface.blockId());
                    if (fertility != null) {
                        int overlay = soilPalette.color(
                                fertility.tier()
                        ).getRGB();
                        raster.setArgb(
                                imageX,
                                imageY,
                                blend(
                                        raster.argbAt(
                                                imageX,
                                                imageY
                                        ),
                                        overlay
                                )
                        );
                    }
                }
            }
        }

        return new RenderedMapTile(
                coordinate,
                bounds,
                image,
                lod,
                drawn,
                style
        );
    }

    private SurfaceSample surfaceAt(
            MapTileData data,
            int worldX,
            int worldZ
    ) {
        MapChunkCoordinate coordinate =
                MapChunkCoordinate.fromWorld(worldX, worldZ);
        SurfaceCacheTile tile =
                data.surfaceTiles().get(coordinate);
        if (tile == null) {
            return null;
        }

        int localX = Math.floorMod(worldX, MapChunk.SIZE);
        int localZ = Math.floorMod(worldZ, MapChunk.SIZE);
        if (localX >= tile.width() || localZ >= tile.height()) {
            return null;
        }

        int index = localZ * tile.width() + localX;
        if ((tile.stateAtIndex(index) & SurfaceCacheTile.RESOLVED) == 0) {
            return null;
        }
        return new SurfaceSample(
                tile.blockIdAtIndex(index),
                SurfaceClassCode.decode(
                        tile.surfaceClassCodeAtIndex(index)
                )
        );
    }

    private double hillshade(
            Map<MapChunkCoordinate, TerrainHeightTile> samples,
            int worldX,
            int worldZ,
            int sampleStep
    ) {
        TerrainSample west = sampleAt(samples, worldX - sampleStep, worldZ);
        TerrainSample east = sampleAt(samples, worldX + sampleStep, worldZ);
        TerrainSample north = sampleAt(samples, worldX, worldZ - sampleStep);
        TerrainSample south = sampleAt(samples, worldX, worldZ + sampleStep);
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

    private int blend(int background, int overlay) {
        int alpha = (overlay >>> 24) & 0xFF;
        if (alpha == 255) {
            return overlay;
        }
        if (alpha == 0) {
            return background;
        }

        int inverse = 255 - alpha;
        int red = (((overlay >>> 16) & 0xFF) * alpha
                + ((background >>> 16) & 0xFF) * inverse) / 255;
        int green = (((overlay >>> 8) & 0xFF) * alpha
                + ((background >>> 8) & 0xFF) * inverse) / 255;
        int blue = ((overlay & 0xFF) * alpha
                + (background & 0xFF) * inverse) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static long divideCeil(long value, int divisor) {
        return Math.floorDiv(
                Math.addExact(value, divisor - 1L),
                divisor
        );
    }

    private static int sampleCoordinate(
            long min,
            long maxExclusive,
            int sampleIndex,
            int blocksPerPixel
    ) {
        long start = Math.addExact(
                min,
                Math.multiplyExact((long) sampleIndex, blocksPerPixel)
        );
        long midpoint = Math.addExact(start, blocksPerPixel / 2L);
        return Math.toIntExact(
                Math.min(midpoint, maxExclusive - 1L)
        );
    }

    private record TerrainSample(int height) {
    }

    private record SurfaceSample(
            int blockId,
            SurfaceClass surfaceClass
    ) {
    }
}
