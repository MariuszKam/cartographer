package cartographer.render;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldPosition;
import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MapRendererTest {

    @Test
    void geometryMatchesTheRendererWorldWindow() {
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(-15.25, 0.0, -20.75),
                HomeState.absent(),
                List.of(),
                new RenderOptions(10, 1, RenderStyle.SIMPLE, Set.of()),
                ProgressReporter.NONE
        );

        MapViewportGeometry geometry = rendered.geometry();
        assertEquals(rendered.image().getWidth(), geometry.imageWidth());
        assertEquals(rendered.image().getHeight(), geometry.imageHeight());
        assertEquals(-26.0, geometry.worldMinX());
        assertEquals(-31.0, geometry.worldMinZ());
        assertEquals(-6.0, geometry.worldMaxXExclusive());
        assertEquals(-11.0, geometry.worldMaxZExclusive());
        assertEquals(32.0, geometry.absoluteWorldXToImageX(-16.0));
        assertEquals(32.0, geometry.absoluteWorldZToImageY(-21.0));
    }

    @Test
    void geometryUsesClampedImageDimensionsAndFractionalCenterWindow() {
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(100.75, 0.0, 100.25),
                HomeState.absent(),
                List.of(),
                new RenderOptions(1, 1, RenderStyle.SIMPLE, Set.of()),
                ProgressReporter.NONE
        );

        assertEquals(64, rendered.image().getWidth());
        assertEquals(99.0, rendered.geometry().worldMinX());
        assertEquals(99.0, rendered.geometry().worldMinZ());
        assertEquals(101.0, rendered.geometry().worldMaxXExclusive());
        assertEquals(101.0, rendered.geometry().worldMaxZExclusive());
    }

    @Test
    void fillsContiguousMapChunksWithoutBackgroundSeams() {
        RenderOptions options =
                new RenderOptions(
                        32,
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        Set.of(RenderLayer.TERRAIN)
                );

        BufferedImage image =
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        32.0,
                                        0.0,
                                        32.0
                                ),
                                HomeState.absent(),
                                List.of(
                                        chunk(0, 0, 70),
                                        chunk(1, 0, 90),
                                        chunk(0, 1, 110),
                                        chunk(1, 1, 130)
                                ),
                                options,
                                ProgressReporter.NONE
                        )
                        .image();

        TerrainPalette palette =
                new TerrainPalette();

        int background =
                palette.background(
                        RenderStyle.TOPOGRAPHIC
                );

        for (int y = 0;
             y < image.getHeight();
             y++) {

            for (int x = 0;
                 x < image.getWidth();
                 x++) {

                assertNotEquals(
                        background,
                        image.getRGB(
                                x,
                                y
                        )
                );
            }
        }
    }

    @Test
    void topographicPaletteUsesNormalizedHeightContrast() {
        TerrainPalette palette =
                new TerrainPalette();

        int low =
                palette.terrainColor(
                        80,
                        80,
                        160,
                        0.0,
                        RenderStyle.TOPOGRAPHIC
                );

        int high =
                palette.terrainColor(
                        160,
                        80,
                        160,
                        0.0,
                        RenderStyle.TOPOGRAPHIC
                );

        assertNotEquals(
                low,
                high
        );
    }

    @Test
    void directRasterBackgroundStillUsesTerrainPalette() {
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                new RenderOptions(16, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.TERRAIN)),
                ProgressReporter.NONE
        );
        int background = new TerrainPalette().background(RenderStyle.SIMPLE);
        assertEquals(background, rendered.image().getRGB(0, 0));
        assertEquals(background, rendered.image().getRGB(63, 63));
    }

    @Test
    void directRasterTerrainUsesExpectedPaletteColor() {
        RenderOptions options = new RenderOptions(
                16, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.TERRAIN)
        );
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(chunk(0, 0, 80)),
                options,
                ProgressReporter.NONE
        );
        int expected = new TerrainPalette().terrainColor(
                112, 80, 142, -2.6 / 32.0, RenderStyle.SIMPLE
        );
        assertEquals(expected, rendered.image().getRGB(32, 32));
    }

    @Test
    void semanticSurfaceUsesExactScaledRectangle() {
        RenderOptions options = new RenderOptions(
                16, 2, RenderStyle.SIMPLE, Set.of(RenderLayer.SURFACE)
        );
        int color = new SemanticTerrainPalette().color(SurfaceClass.ROCK, 0.0);
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(
                        16, 80, 16, BlockInfo.unknown(1), 0,
                        BlockInfo.unknown(0), SurfaceClass.ROCK
                )),
                options,
                ProgressReporter.NONE
        );
        assertEquals(color, rendered.image().getRGB(32, 32));
        assertEquals(color, rendered.image().getRGB(33, 33));
        assertNotEquals(color, rendered.image().getRGB(35, 32));
    }

    @Test
    void soilFertilityUsesExactScaledRectangleWithoutSurfaceLayer() {
        RenderOptions options = new RenderOptions(
                16, 2, RenderStyle.SIMPLE, Set.of(RenderLayer.SOIL_FERTILITY)
        );
        int background = new TerrainPalette().background(RenderStyle.SIMPLE);
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(
                        16, 80, 16,
                        new BlockInfo(1, "game:soil-medium-normal")
                )),
                options,
                ProgressReporter.NONE
        );

        assertNotEquals(background, rendered.image().getRGB(32, 32));
        assertEquals(rendered.image().getRGB(32, 32), rendered.image().getRGB(33, 33));
        assertEquals(background, rendered.image().getRGB(35, 32));
    }

    @Test
    void unknownAndNonSoilBlocksRemainUnpainted() {
        RenderOptions options = new RenderOptions(
                16, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.SOIL_FERTILITY)
        );
        int background = new TerrainPalette().background(RenderStyle.SIMPLE);
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(
                        new SurfaceCell(16, 80, 16,
                                new BlockInfo(1, "game:rock-granite")),
                        new SurfaceCell(17, 80, 16,
                                new BlockInfo(2, "creativegrass-medium-normal"))
                ),
                options,
                ProgressReporter.NONE
        );

        assertEquals(background, rendered.image().getRGB(32, 32));
        assertEquals(background, rendered.image().getRGB(33, 32));
    }

    @Test
    void differentFertilityTiersRenderDifferently() {
        RenderOptions options = new RenderOptions(
                16, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.SOIL_FERTILITY)
        );
        int low = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(16, 80, 16,
                        new BlockInfo(1, "game:soil-low-normal"))),
                options,
                ProgressReporter.NONE
        ).image().getRGB(32, 32);
        int high = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(16, 80, 16,
                        new BlockInfo(1, "game:soil-high-normal"))),
                options,
                ProgressReporter.NONE
        ).image().getRGB(32, 32);

        assertNotEquals(low, high);
    }

    @Test
    void soilFertilityRendersAboveSurface() {
        RenderOptions options = new RenderOptions(
                16, 1, RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.SOIL_FERTILITY)
        );
        SurfaceCell block = new SurfaceCell(
                16, 80, 16,
                new BlockInfo(1, "game:soil-medium-normal"),
                0,
                BlockInfo.unknown(0),
                SurfaceClass.SOIL
        );
        int surfaceColor = new SemanticTerrainPalette().color(SurfaceClass.SOIL, 0.0);
        int actual = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(block),
                options,
                ProgressReporter.NONE
        ).image().getRGB(32, 32);

        assertNotEquals(surfaceColor, actual);
    }

    @Test
    void markersRemainAboveSoilFertility() {
        RenderOptions options = new RenderOptions(
                16, 1, RenderStyle.SIMPLE,
                Set.of(RenderLayer.SOIL_FERTILITY, RenderLayer.MARKERS)
        );
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(16, 80, 16,
                        new BlockInfo(1, "game:soil-medium-normal"))),
                options,
                ProgressReporter.NONE
        );

        assertEquals(Color.RED.getRGB(), rendered.image().getRGB(32, 32));
    }

    @Test
    void soilFertilityLegendUsesUpperRightAndCoexistsWithSurfaceLegend() {
        RenderOptions options = new RenderOptions(
                128, 1, RenderStyle.SIMPLE,
                Set.of(RenderLayer.SURFACE, RenderLayer.SOIL_FERTILITY)
        );
        int background = new TerrainPalette().background(RenderStyle.SIMPLE);
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(128.0, 0.0, 128.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceCell(
                        128, 80, 128,
                        new BlockInfo(1, "game:soil-medium-normal"),
                        0,
                        BlockInfo.unknown(0),
                        SurfaceClass.SOIL
                )),
                options,
                ProgressReporter.NONE
        );

        assertNotEquals(background, rendered.image().getRGB(
                rendered.image().getWidth() - 135, 12
        ));
        assertNotEquals(background, rendered.image().getRGB(
                12, rendered.image().getHeight() - 20
        ));
    }

    @Test
    void preparedTerrainRenderingMatchesChunkRendering() {
        WorldPosition center = new WorldPosition(32.0, 0.0, 32.0);
        RenderOptions options = new RenderOptions(
                32,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN)
        );
        List<MapChunk> chunks = List.of(
                chunk(0, 0, 70),
                chunk(1, 0, 90),
                chunk(0, 1, 110),
                chunk(1, 1, 130)
        );

        RenderedMap listRendered = new MapRenderer().render(
                center,
                HomeState.absent(),
                chunks,
                options,
                ProgressReporter.NONE
        );
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                center, options, chunks.size()
        );
        chunks.forEach(builder::accept);
        RenderedMap preparedRendered = new MapRenderer().render(
                center,
                center,
                HomeState.absent(),
                builder.finish(),
                SurfaceRenderData.empty(
                        RenderSamplingPlan.from(center, options)
                ),
                null,
                Map.of(),
                options,
                ProgressReporter.NONE
        );

        assertEquals(listRendered.image().getWidth(), preparedRendered.image().getWidth());
        assertEquals(listRendered.image().getHeight(), preparedRendered.image().getHeight());
        assertEquals(listRendered.report(), preparedRendered.report());
        for (int y = 0; y < listRendered.image().getHeight(); y++) {
            for (int x = 0; x < listRendered.image().getWidth(); x++) {
                assertEquals(
                        listRendered.image().getRGB(x, y),
                        preparedRendered.image().getRGB(x, y)
                );
            }
        }
    }

    @Test
    void denseHeightGridKeepsTerrainOutputStableAcrossChunkBoundary() {
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(32.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(chunk(0, 0, 20), chunk(1, 0, 120)),
                new RenderOptions(32, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.TERRAIN)),
                ProgressReporter.NONE
        );

        assertNotEquals(
                new TerrainPalette().background(RenderStyle.SIMPLE),
                rendered.image().getRGB(31, 32)
        );
        assertNotEquals(
                new TerrainPalette().background(RenderStyle.SIMPLE),
                rendered.image().getRGB(32, 32)
        );
        assertNotEquals(
                rendered.image().getRGB(31, 32),
                rendered.image().getRGB(32, 32)
        );
    }

    @Test
    void hillshadeFallsBackToZeroWhenNeighborSampleMissing() {
        RenderedMap rendered = renderWithSurface(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(chunk(0, 0, 80)),
                List.of(new SurfaceCell(
                        0, 80, 16, BlockInfo.unknown(1), 0,
                        BlockInfo.unknown(0), SurfaceClass.UNKNOWN
                )),
                new RenderOptions(
                        16, 1, RenderStyle.SIMPLE,
                        Set.of(RenderLayer.SURFACE)
                ),
                ProgressReporter.NONE
        );

        int expected = new SemanticTerrainPalette().color(
                SurfaceClass.UNKNOWN, 0.0
        );
        assertEquals(expected, rendered.image().getRGB(0, 32));
    }

    @Test
    void worldWindowClippingStillWorks() {
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(chunk(0, 0, 10), chunk(1, 0, 1000)),
                new RenderOptions(16, 1, RenderStyle.SIMPLE, Set.of(RenderLayer.TERRAIN)),
                ProgressReporter.NONE
        );

        assertEquals(64 * 64, rendered.report().tilesDrawn());
    }

    @Test
    void drawsPlayerMarkerAtAbsoluteCenterWithoutHome() {
        RenderedMap rendered =
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        100.0,
                                        0.0,
                                        100.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                new RenderOptions(
                                        10,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(
                                                RenderLayer.MARKERS
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        assertEquals(
                1,
                rendered.report()
                        .markerCount()
        );

        assertEquals(
                Color.RED.getRGB(),
                rendered.image()
                        .getRGB(
                                32,
                                32
                        )
        );
        assertEquals(
                32,
                Math.round(
                        rendered.geometry().absoluteWorldXToImageX(100.0)
                )
        );
        assertEquals(
                32,
                Math.round(
                        rendered.geometry().absoluteWorldZToImageY(100.0)
                )
        );
    }

    @Test
    void drawsHomeMarkerInAbsoluteRendererCoordinates() {
        RenderedMap rendered =
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        100.0,
                                        0.0,
                                        100.0
                                ),
                                HomeState.present(
                                        new HomeLocation(
                                                105.0,
                                                100.0
                                        )
                                ),
                                List.of(),
                                new RenderOptions(
                                        10,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(
                                                RenderLayer.MARKERS
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        assertEquals(
                2,
                rendered.report()
                        .markerCount()
        );

        assertEquals(
                Color.CYAN.getRGB(),
                rendered.image()
                        .getRGB(
                                48,
                                32
                        )
        );
    }

    @Test
    void drawsPlayerMarkerAtActualPlayerPositionWhenRenderCenterDiffers() {
        RenderedMap rendered =
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        100.0,
                                        0.0,
                                        100.0
                                ),
                                new WorldPosition(
                                        105.0,
                                        0.0,
                                        100.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                new RenderOptions(
                                        10,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(
                                                RenderLayer.MARKERS
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        assertEquals(
                Color.RED.getRGB(),
                rendered.image()
                        .getRGB(
                                48,
                                32
                        )
        );
    }

    @Test
    void semanticSurfaceLayerUsesClassPaletteOverHeightTerrain() {
        RenderedMap rendered =
                renderWithSurface(
                                new WorldPosition(
                                        16.0,
                                        0.0,
                                        16.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                List.of(
                                        new SurfaceCell(
                                                16,
                                                80,
                                                16,
                                                new BlockInfo(
                                                        10,
                                                        "water-still-7"
                                                ),
                                                10,
                                                new BlockInfo(
                                                        10,
                                                        "water-still-7"
                                                ),
                                                SurfaceClass.WATER
                                        )
                                ),
                                new RenderOptions(
                                        16,
                                        1,
                                        RenderStyle.TOPOGRAPHIC,
                                        Set.of(
                                                RenderLayer.TERRAIN,
                                                RenderLayer.SURFACE
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        int expected =
                new SemanticTerrainPalette()
                        .color(
                                SurfaceClass.WATER,
                                0.0
                        );

        assertEquals(
                expected,
                rendered.image()
                        .getRGB(
                                32,
                                32
                        )
        );
    }

    @Test
    void semanticSurfaceLegendDrawsWhenClassesArePresent() {
        RenderedMap rendered =
                renderWithSurface(
                                new WorldPosition(
                                        128.0,
                                        0.0,
                                        128.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                List.of(
                                        new SurfaceCell(
                                                128,
                                                80,
                                                128,
                                                new BlockInfo(
                                                        99,
                                                        "unknown:99"
                                                ),
                                                0,
                                                BlockInfo.unknown(0),
                                                SurfaceClass.UNKNOWN
                                        )
                                ),
                                new RenderOptions(
                                        128,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(
                                                RenderLayer.SURFACE
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        int background =
                new TerrainPalette()
                        .background(
                                RenderStyle.SIMPLE
                        );

        assertNotEquals(
                background,
                rendered.image()
                        .getRGB(
                                10,
                                rendered.image()
                                        .getHeight()
                                        - 20
                        )
        );
    }


    private RenderedMap renderWithSurface(
            WorldPosition center,
            HomeState home,
            List<MapChunk> chunks,
            List<SurfaceCell> surfaceBlocks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        MapTerrainPreparation.Builder terrain =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        chunks.size(),
                        progress
                );
        chunks.forEach(terrain::accept);

        WorldMetadata metadata = new WorldMetadata(1024, 256, 1024);
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                center.x(),
                center.z(),
                options.radiusBlocks(),
                metadata
        );
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(layout);
        Map<Integer, BlockInfo> registry = new HashMap<>();
        for (SurfaceCell block : surfaceBlocks) {
            accumulator.recordSurface(
                    block.worldX(),
                    block.worldZ(),
                    block.y(),
                    block.blockInfo().id(),
                    block.liquidBlockId(),
                    block.surfaceClass()
            );
            registry.put(block.blockInfo().id(), block.blockInfo());
            registry.put(block.liquidBlockId(), block.liquidBlockInfo());
        }

        SurfaceMap surface = accumulator.finish();
        SurfaceRenderData renderData = SurfaceRenderData.from(
                surface,
                RenderSamplingPlan.from(center, options)
        );

        return new MapRenderer().render(
                center,
                center,
                home,
                terrain.finish(),
                renderData,
                surface,
                registry,
                options,
                progress
        );
    }

    private record SurfaceCell(
            int worldX,
            int y,
            int worldZ,
            BlockInfo blockInfo,
            int liquidBlockId,
            BlockInfo liquidBlockInfo,
            SurfaceClass surfaceClass
    ) {
        private SurfaceCell(
                int worldX,
                int y,
                int worldZ,
                BlockInfo blockInfo
        ) {
            this(
                    worldX,
                    y,
                    worldZ,
                    blockInfo,
                    0,
                    BlockInfo.unknown(0),
                    SurfaceClass.UNKNOWN
            );
        }
    }

    private MapChunk chunk(
            int chunkX,
            int chunkZ,
            int baseHeight
    ) {
        int[] heights =
                new int[MapChunk.HEIGHT_VALUE_COUNT];

        for (int z = 0;
             z < MapChunk.SIZE;
             z++) {

            for (int x = 0;
                 x < MapChunk.SIZE;
                 x++) {

                heights[z * MapChunk.SIZE + x] =
                        baseHeight
                                + x
                                + z;
            }
        }

        return new MapChunk(
                new MapChunkCoordinate(
                        chunkX,
                        chunkZ
                ),
                heights,
                new int[0]
        );
    }
}
