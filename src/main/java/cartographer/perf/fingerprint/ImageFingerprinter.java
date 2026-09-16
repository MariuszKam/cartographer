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
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        CanonicalWriter writer = new CanonicalWriter()
                .writeInt(width)
                .writeInt(height)
                .writeSequenceStart(argb.length);
        for (int pixel : argb) {
            writer.writeInt(pixel);
        }
        return SemanticFingerprinter.fingerprint(writer);
    }
}
