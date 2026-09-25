package cartographer.render;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SoilFertilityOverlayRendererTest {
    private static final int BACKGROUND = 0xFF112233;

    private final SoilFertilityOverlayRenderer renderer =
            new SoilFertilityOverlayRenderer();

    @Test
    void waterObscuresOtherwiseClassifiableFarmland() {
        BufferedImage image = image();

        int drawn = draw(
                image,
                new BlockInfo(1, "game:farmland-moist-high"),
                2,
                new BlockInfo(2, "game:water-still-7"),
                SurfaceClass.WATER
        );

        assertEquals(0, drawn);
        assertEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void snowDoesNotInferUnderlyingFertility() {
        BufferedImage image = image();

        int drawn = draw(
                image,
                new BlockInfo(1, "game:snowlayer-1"),
                0,
                BlockInfo.unknown(0),
                SurfaceClass.SNOW
        );

        assertEquals(0, drawn);
        assertEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void farmlandStillRendersWhenSurfaceClassIsUnknown() {
        BufferedImage image = image();

        int drawn = draw(
                image,
                new BlockInfo(1, "game:farmland-moist-high"),
                0,
                BlockInfo.unknown(0),
                SurfaceClass.UNKNOWN
        );

        assertEquals(1, drawn);
        assertNotEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void forestFloorStillRenders() {
        BufferedImage image = image();

        int drawn = draw(
                image,
                new BlockInfo(1, "game:forestfloor-7"),
                0,
                BlockInfo.unknown(0),
                SurfaceClass.FOREST_FLOOR
        );

        assertEquals(1, drawn);
        assertNotEquals(BACKGROUND, image.getRGB(2, 3));
    }

    private int draw(
            BufferedImage image,
            BlockInfo block,
            int liquidBlockId,
            BlockInfo liquidBlock,
            SurfaceClass surfaceClass
    ) {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                8,
                8,
                8,
                new WorldMetadata(16, 256, 16)
        );
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(
                2,
                3,
                80,
                block.id(),
                liquidBlockId,
                surfaceClass
        );
        SurfaceMap surface = accumulator.finish();

        Map<Integer, BlockInfo> registry = new HashMap<>();
        registry.put(block.id(), block);
        registry.put(liquidBlockId, liquidBlock);

        return renderer.draw(
                image,
                surface,
                registry,
                0,
                0,
                1.0,
                ProgressReporter.NONE
        );
    }

    private BufferedImage image() {
        BufferedImage image = new BufferedImage(
                16,
                16,
                BufferedImage.TYPE_INT_ARGB
        );
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, BACKGROUND);
            }
        }
        return image;
    }
}
