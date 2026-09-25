package cartographer.render;

import cartographer.progress.ProgressReporter;
import cartographer.model.HomeState;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticMapRendererTest {

    @Test
    void semanticBlockFillsItsScaledPixelArea() {
        WorldPosition center =
                new WorldPosition(
                        16.0,
                        0.0,
                        16.0
                );
        RenderOptions options =
                new RenderOptions(
                        16,
                        2,
                        RenderStyle.TOPOGRAPHIC,
                        Set.of(
                                RenderLayer.SURFACE
                        )
                );
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(
                        SurfaceTileLayout.forSurface(
                                center.x(),
                                center.z(),
                                options.radiusBlocks(),
                                new WorldMetadata(64, 256, 64)
                        )
                );
        accumulator.recordSurface(
                16,
                16,
                80,
                10,
                0,
                SurfaceClass.SOIL
        );
        SurfaceMap surface =
                accumulator.finish();

        RenderedMap rendered =
                new MapRenderer()
                        .render(
                                center,
                                center,
                                HomeState.absent(),
                                MapTerrainPreparation.builder(
                                        center,
                                        options,
                                        0,
                                        ProgressReporter.NONE
                                ).finish(),
                                SurfaceRenderData.from(
                                        surface,
                                        RenderSamplingPlan.from(
                                                center,
                                                options
                                        )
                                ),
                                null,
                                Map.of(),
                                options,
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

        assertEquals(
                expected,
                image.getRGB(
                        33,
                        33
                )
        );
    }
}
