package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.List;
import java.util.Optional;
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
                                Optional.empty(),
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

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
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
    void drawsPlayerMarkerAtAbsoluteCenterWithoutHome() {
        RenderedMap rendered =
                new MapRenderer()
                        .render(
                                new WorldPosition(
                                        100.0,
                                        0.0,
                                        100.0
                                ),
                                Optional.empty(),
                                List.of(),
                                new RenderOptions(
                                        10,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(RenderLayer.MARKERS)
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
                                Optional.of(
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
                                        Set.of(RenderLayer.MARKERS)
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
                                Optional.empty(),
                                List.of(),
                                new RenderOptions(
                                        10,
                                        1,
                                        RenderStyle.SIMPLE,
                                        Set.of(RenderLayer.MARKERS)
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

    private MapChunk chunk(
            int chunkX,
            int chunkZ,
            int baseHeight
    ) {
        int[] heights =
                new int[MapChunk.HEIGHT_VALUE_COUNT];

        for (int z = 0; z < MapChunk.SIZE; z++) {
            for (int x = 0; x < MapChunk.SIZE; x++) {
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
