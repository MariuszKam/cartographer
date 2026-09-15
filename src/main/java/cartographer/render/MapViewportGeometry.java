package cartographer.render;

/**
 * Affine mapping between an image and the absolute world area represented by
 * its content rectangle.
 *
 * <p>Image coordinates are natural image coordinates, with the origin at the
 * upper-left corner. World maximum bounds are exclusive.</p>
 */
public record MapViewportGeometry(
        int imageWidth,
        int imageHeight,
        int contentX,
        int contentY,
        int contentWidth,
        int contentHeight,
        double worldMinX,
        double worldMinZ,
        double worldMaxXExclusive,
        double worldMaxZExclusive
) {

    public MapViewportGeometry {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (contentWidth <= 0 || contentHeight <= 0) {
            throw new IllegalArgumentException("Content dimensions must be positive");
        }
        if (contentX < 0
                || contentY < 0
                || (long) contentX + contentWidth > imageWidth
                || (long) contentY + contentHeight > imageHeight) {
            throw new IllegalArgumentException("Content rectangle must be inside the image");
        }
        if (!Double.isFinite(worldMinX)
                || !Double.isFinite(worldMinZ)
                || !Double.isFinite(worldMaxXExclusive)
                || !Double.isFinite(worldMaxZExclusive)) {
            throw new IllegalArgumentException("World bounds must be finite");
        }
        if (worldMaxXExclusive <= worldMinX
                || worldMaxZExclusive <= worldMinZ) {
            throw new IllegalArgumentException("World bounds must be non-empty");
        }
    }

    public static MapViewportGeometry fullImage(
            int imageWidth,
            int imageHeight,
            double worldMinX,
            double worldMinZ,
            double worldMaxXExclusive,
            double worldMaxZExclusive
    ) {
        return new MapViewportGeometry(
                imageWidth,
                imageHeight,
                0,
                0,
                imageWidth,
                imageHeight,
                worldMinX,
                worldMinZ,
                worldMaxXExclusive,
                worldMaxZExclusive
        );
    }

    public double absoluteWorldXToImageX(double worldX) {
        return contentX
                + (worldX - worldMinX) * contentWidth
                / (worldMaxXExclusive - worldMinX);
    }

    public double absoluteWorldZToImageY(double worldZ) {
        return contentY
                + (worldZ - worldMinZ) * contentHeight
                / (worldMaxZExclusive - worldMinZ);
    }

    public double imageXToAbsoluteWorldX(double imageX) {
        return worldMinX
                + (imageX - contentX) * (worldMaxXExclusive - worldMinX)
                / contentWidth;
    }

    public double imageYToAbsoluteWorldZ(double imageY) {
        return worldMinZ
                + (imageY - contentY) * (worldMaxZExclusive - worldMinZ)
                / contentHeight;
    }

    public boolean containsImagePoint(double imageX, double imageY) {
        return imageX >= contentX
                && imageX < contentX + contentWidth
                && imageY >= contentY
                && imageY < contentY + contentHeight;
    }

    public boolean containsAbsoluteWorldPoint(double worldX, double worldZ) {
        return worldX >= worldMinX
                && worldX < worldMaxXExclusive
                && worldZ >= worldMinZ
                && worldZ < worldMaxZExclusive;
    }
}
