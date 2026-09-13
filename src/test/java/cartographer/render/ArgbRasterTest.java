package cartographer.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgbRasterTest {
    @Test
    void setArgbChangesOriginalBufferedImage() {
        BufferedImage image = image(4, 3);
        ArgbRaster.wrap(image).setArgb(2, 1, 0x7F123456);
        assertEquals(0x7F123456, image.getRGB(2, 1));
    }

    @Test
    void fillRowWritesWholeRequestedRowOnly() {
        BufferedImage image = image(3, 3);
        ArgbRaster raster = ArgbRaster.wrap(image);
        raster.fillRow(1, 0xFF112233);
        for (int x = 0; x < 3; x++) {
            assertEquals(0xFF112233, image.getRGB(x, 1));
            assertEquals(0, image.getRGB(x, 0));
            assertEquals(0, image.getRGB(x, 2));
        }
    }

    @Test
    void fillRectUsesEndExclusiveCoordinates() {
        BufferedImage image = image(4, 4);
        ArgbRaster.wrap(image).fillRect(1, 1, 3, 3, 0xFF010203);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(
                        x >= 1 && x < 3 && y >= 1 && y < 3
                                ? 0xFF010203 : 0,
                        image.getRGB(x, y)
                );
            }
        }
    }

    @Test
    void preservesAlphaBitsExactly() {
        BufferedImage image = image(1, 1);
        ArgbRaster.wrap(image).setArgb(0, 0, 0x7F123456);
        assertEquals(0x7F123456, image.getRGB(0, 0));
    }

    @Test
    void rejectsUnsupportedImageType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ArgbRaster.wrap(new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR))
        );
    }

    @Test
    void rejectsInvalidBounds() {
        ArgbRaster raster = ArgbRaster.wrap(image(2, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> raster.setArgb(-1, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> raster.setArgb(2, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> raster.fillRow(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> raster.fillRect(0, 0, 3, 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> raster.fillRect(0, 0, 1, 3, 1));
    }

    @Test
    void graphics2dCanDrawOverDirectRasterPixels() {
        BufferedImage image = image(4, 4);
        ArgbRaster.wrap(image).fillRect(0, 0, 4, 4, Color.BLUE.getRGB());
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(1, 1, 2, 2);
        } finally {
            graphics.dispose();
        }
        assertEquals(Color.RED.getRGB(), image.getRGB(1, 1));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(0, 0));
    }

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }
}
