package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.HomeState;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticMapRendererTest {

    @Test
    void semanticBlockFillsItsScaledPixelArea() {
        SurfaceBlock block =
                new SurfaceBlock(
                        16,
                        80,
                        16,
                        new BlockInfo(
                                10,
                                "soil-low-normal"
                        ),
                        0,
                        BlockInfo.unknown(0),
                        SurfaceClass.SOIL
                );

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
                                        block
                                ),
                                new RenderOptions(
                                        16,
                                        2,
                                        RenderStyle.TOPOGRAPHIC,
                                        Set.of(
                                                RenderLayer.SURFACE
                                        )
                                ),
                                ProgressReporter.NONE
                        );

        BufferedImage image =
                rendered.image();

        int expected =
                new SemanticTerrainPalette()
                        .color(
                                SurfaceClass.SOIL,
                                0.0
                        );

        assertEquals(
                expected,
                image.getRGB(
                        32,
                        32
                )
        );

        /*
         * This pixel belongs to the same world block at scale > 1.
         * The old renderer left it as background.
         */
        assertEquals(
                expected,
                image.getRGB(
                        33,
                        33
                )
        );
    }
}