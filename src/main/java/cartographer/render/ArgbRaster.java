package cartographer.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Objects;

final class ArgbRaster {
    private final int[] pixels;
    private final int width;
    private final int height;

    private ArgbRaster(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    static ArgbRaster wrap(BufferedImage image) {
        Objects.requireNonNull(image, "image is required");
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            throw new IllegalArgumentException(
                    "image must use TYPE_INT_ARGB"
            );
        }

        DataBufferInt dataBuffer = getDataBuffer(image);

        int required = Math.multiplyExact(image.getWidth(), image.getHeight());
        int[] pixels = dataBuffer.getData();
        if (pixels.length < required) {
            throw new IllegalArgumentException(
                    "image raster does not contain the complete image"
            );
        }
        return new ArgbRaster(pixels, image.getWidth(), image.getHeight());
    }

    private static DataBufferInt getDataBuffer(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        if (!(raster.getDataBuffer() instanceof DataBufferInt dataBuffer)
                || !(raster.getSampleModel() instanceof SinglePixelPackedSampleModel sampleModel)) {
            throw new IllegalArgumentException(
                    "image must use a packed integer raster"
            );
        }
        if (raster.getMinX() != 0 || raster.getMinY() != 0
                || sampleModel.getScanlineStride() != image.getWidth()
                || dataBuffer.getOffset() != 0) {
            throw new IllegalArgumentException(
                    "image raster layout is not directly addressable"
            );
        }
        return dataBuffer;
    }

    void setArgb(int x, int y, int argb) {
        checkPixel(x, y);
        pixels[y * width + x] = argb;
    }

    void fillRow(int y, int argb) {
        if (y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("row out of bounds: " + y);
        }
        Arrays.fill(pixels, y * width, (y + 1) * width, argb);
    }

    void fillRect(int startX, int startY, int endX, int endY, int argb) {
        if (startX < 0 || startX > endX || endX > width
                || startY < 0 || startY > endY || endY > height) {
            throw new IndexOutOfBoundsException(
                    "rectangle is outside raster bounds"
            );
        }
        for (int y = startY; y < endY; y++) {
            Arrays.fill(
                    pixels,
                    y * width + startX,
                    y * width + endX,
                    argb
            );
        }
    }

    int height() {
        return height;
    }

    private void checkPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException(
                    "pixel out of bounds: " + x + "," + y
            );
        }
    }
}
