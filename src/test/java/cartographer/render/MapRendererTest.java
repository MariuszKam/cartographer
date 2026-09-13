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
