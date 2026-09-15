package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MapRendererTest {

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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(
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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(
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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(
                        new SurfaceBlock(16, 80, 16,
                                new BlockInfo(1, "game:rock-granite")),
                        new SurfaceBlock(17, 80, 16,
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
        MapRenderer renderer = new MapRenderer();
        int low = renderer.render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(16, 80, 16,
                        new BlockInfo(1, "game:soil-low-normal"))),
                options,
                ProgressReporter.NONE
        ).image().getRGB(32, 32);
        int high = renderer.render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(16, 80, 16,
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
        SurfaceBlock block = new SurfaceBlock(
                16, 80, 16,
                new BlockInfo(1, "game:soil-medium-normal"),
                0,
                BlockInfo.unknown(0),
                SurfaceClass.SOIL
        );
        int surfaceColor = new SemanticTerrainPalette().color(SurfaceClass.SOIL, 0.0);
        int actual = new MapRenderer().render(
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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(16, 80, 16,
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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(128.0, 0.0, 128.0),
                HomeState.absent(),
                List.of(),
                List.of(new SurfaceBlock(
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
    void preparedTerrainRenderingMatchesListBasedRendering() {
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
                List.of(),
                options
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
        RenderedMap rendered = new MapRenderer().render(
                new WorldPosition(16.0, 0.0, 16.0),
                HomeState.absent(),
                List.of(chunk(0, 0, 80)),
                List.of(new SurfaceBlock(
                        0, 80, 0, BlockInfo.unknown(1), 0,
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
        assertEquals(expected, rendered.image().getRGB(0, 0));
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
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        16.0,
                                        0.0,
                                        16.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                List.of(
                                        new SurfaceBlock(
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
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        128.0,
                                        0.0,
                                        128.0
                                ),
                                HomeState.absent(),
                                List.of(),
                                List.of(
                                        new SurfaceBlock(
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
