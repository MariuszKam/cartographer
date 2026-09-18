package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;

import java.util.Locale;
import java.util.Objects;

final class MapScaleSummary {
    private static final double SAME_SCALE_EPSILON = 1.0e-9;

    private MapScaleSummary() {
    }

    static String format(MapViewportGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry is required");
        String area = wholeNumber(geometry.worldWidthBlocks())
                + "×" + wholeNumber(geometry.worldHeightBlocks())
                + " blocks";
        String raster = geometry.imageWidth()
                + "×" + geometry.imageHeight()
                + " px";
        double xScale = geometry.blocksPerPixelX();
        double zScale = geometry.blocksPerPixelZ();
        String scale = Math.abs(xScale - zScale) <= SAME_SCALE_EPSILON
                ? String.format(Locale.ROOT, "%.2f blk/px", xScale)
                : String.format(Locale.ROOT, "%.2f×%.2f blk/px", xScale, zScale);
        return "Area " + area + " | Raster " + raster + " | " + scale;
    }

    private static String wholeNumber(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) <= 1.0e-9) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
