package cartographer.perf.fingerprint;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Fingerprints logical row-major ARGB pixels, never encoded image bytes. */
public final class ImageFingerprinter {
    private ImageFingerprinter() {
    }

    public static ResultFingerprint fingerprint(BufferedImage image) {
        Objects.requireNonNull(image, "image is required");
        int width = image.getWidth();
        int height = image.getHeight();
        int[] row = new int[width];

        return SemanticFingerprinter.fingerprint(writer -> {
            writer.writeInt(width)
                    .writeInt(height)
                    .writeSequenceStart(Math.multiplyExact(width, height));
            for (int y = 0; y < height; y++) {
                image.getRGB(0, y, width, 1, row, 0, width);
                for (int pixel : row) {
                    writer.writeInt(pixel);
                }
            }
        });
    }
}
