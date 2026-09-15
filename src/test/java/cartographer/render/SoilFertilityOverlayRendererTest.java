package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SoilFertilityOverlayRendererTest {
    private static final int BACKGROUND = 0xFF112233;

    private final SoilFertilityOverlayRenderer renderer =
            new SoilFertilityOverlayRenderer();

    @Test
    void waterObscuresOtherwiseClassifiableFarmland() {
        BufferedImage image = image();

        int drawn = renderer.draw(
                image,
                List.of(new SurfaceBlock(
                        2, 80, 3,
                        new BlockInfo(1, "game:farmland-moist-high"),
                        2,
                        new BlockInfo(2, "game:water-still-7"),
                        SurfaceClass.WATER
                )),
                0,
                0,
                1.0,
                ProgressReporter.NONE
        );

        assertEquals(0, drawn);
        assertEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void snowDoesNotInferUnderlyingFertility() {
        BufferedImage image = image();

        int drawn = renderer.draw(
                image,
                List.of(new SurfaceBlock(
                        2, 80, 3,
                        new BlockInfo(1, "game:snowlayer-1"),
                        0,
                        BlockInfo.unknown(0),
                        SurfaceClass.SNOW
                )),
                0,
                0,
                1.0,
                ProgressReporter.NONE
        );

        assertEquals(0, drawn);
        assertEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void farmlandStillRendersWhenSurfaceClassIsUnknown() {
        BufferedImage image = image();

        int drawn = renderer.draw(
                image,
                List.of(new SurfaceBlock(
                        2, 80, 3,
                        new BlockInfo(1, "game:farmland-moist-high"),
                        0,
                        BlockInfo.unknown(0),
                        SurfaceClass.UNKNOWN
                )),
                0,
                0,
                1.0,
                ProgressReporter.NONE
        );

        assertEquals(1, drawn);
        assertNotEquals(BACKGROUND, image.getRGB(2, 3));
    }

    @Test
    void forestFloorStillRenders() {
        BufferedImage image = image();

        int drawn = renderer.draw(
                image,
                List.of(new SurfaceBlock(
                        2, 80, 3,
                        new BlockInfo(1, "game:forestfloor-7"),
                        0,
                        BlockInfo.unknown(0),
                        SurfaceClass.FOREST_FLOOR
                )),
                0,
                0,
                1.0,
                ProgressReporter.NONE
        );

        assertEquals(1, drawn);
        assertNotEquals(BACKGROUND, image.getRGB(2, 3));
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
