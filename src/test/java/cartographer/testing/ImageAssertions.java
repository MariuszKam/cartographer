package cartographer.testing;

import java.awt.image.BufferedImage;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ImageAssertions {
    private ImageAssertions() {
    }

    public static void assertImageEquals(BufferedImage expected, BufferedImage actual) {
        Objects.requireNonNull(expected, "expected image is required");
        Objects.requireNonNull(actual, "actual image is required");
        assertEquals(expected.getWidth(), actual.getWidth(), "image width");
        assertEquals(expected.getHeight(), actual.getHeight(), "image height");

        int width = expected.getWidth();
        int[] expectedRow = new int[width];
        int[] actualRow = new int[width];
        for (int y = 0; y < expected.getHeight(); y++) {
            expected.getRGB(0, y, width, 1, expectedRow, 0, width);
            actual.getRGB(0, y, width, 1, actualRow, 0, width);
            assertArrayEquals(expectedRow, actualRow, "image row " + y);
        }
    }
}
