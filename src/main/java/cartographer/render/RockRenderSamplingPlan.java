package cartographer.render;

import cartographer.model.WorldPosition;

import java.util.Arrays;
import java.util.Objects;

/**
 * Exact ROCK raster sampling contract shared by retained and snapshot-direct
 * render paths.
 */
public final class RockRenderSamplingPlan {
    private final int radius;
    private final int worldDiameter;
    private final int rasterSize;
    private final int minWorldX;
    private final int minWorldZ;
    private final int[] imageByWorldOffset;
    private final MapViewportGeometry geometry;

    private RockRenderSamplingPlan(
            int radius,
            int worldDiameter,
            int rasterSize,
            int minWorldX,
            int minWorldZ,
            int[] imageByWorldOffset,
            MapViewportGeometry geometry
    ) {
        this.radius = radius;
        this.worldDiameter = worldDiameter;
        this.rasterSize = rasterSize;
        this.minWorldX = minWorldX;
        this.minWorldZ = minWorldZ;
        this.imageByWorldOffset = imageByWorldOffset;
        this.geometry = geometry;
    }

    public static RockRenderSamplingPlan from(
            WorldPosition center,
            int radius
    ) {
        return from(center, radius, MapRasterContract.MAX_RASTER_SIZE);
    }

    static RockRenderSamplingPlan from(
            WorldPosition center,
            int radius,
            int maxRasterSize
    ) {
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (maxRasterSize <= 0) {
            throw new IllegalArgumentException(
                    "maxRasterSize must be positive"
            );
        }

        int centerX = floorBlockCoordinate(center.x());
        int centerZ = floorBlockCoordinate(center.z());
        int worldDiameter;
        try {
            worldDiameter = Math.addExact(
                    Math.multiplyExact(radius, 2),
                    1
            );
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "rock map image is too large",
                    failure
            );
        }

        int rasterSize = Math.min(worldDiameter, maxRasterSize);
        int minWorldX = Math.subtractExact(centerX, radius);
        int minWorldZ = Math.subtractExact(centerZ, radius);
        int[] imageByWorldOffset = new int[worldDiameter];
        Arrays.fill(imageByWorldOffset, -1);
        for (int pixel = 0; pixel < rasterSize; pixel++) {
            int offset = sampleOffset(
                    pixel,
                    rasterSize,
                    worldDiameter
            );
            imageByWorldOffset[offset] = pixel;
        }

        return new RockRenderSamplingPlan(
                radius,
                worldDiameter,
                rasterSize,
                minWorldX,
                minWorldZ,
                imageByWorldOffset,
                MapViewportGeometry.fullImage(
                        rasterSize,
                        rasterSize,
                        minWorldX,
                        minWorldZ,
                        minWorldX + (double) worldDiameter,
                        minWorldZ + (double) worldDiameter
                )
        );
    }

    public int radius() {
        return radius;
    }

    public int worldDiameter() {
        return worldDiameter;
    }

    public int rasterSize() {
        return rasterSize;
    }

    public int minWorldX() {
        return minWorldX;
    }

    public int minWorldZ() {
        return minWorldZ;
    }

    public MapViewportGeometry geometry() {
        return geometry;
    }

    public int imageXForWorldX(int worldX) {
        return imageForOffset((long) worldX - minWorldX);
    }

    public int imageYForWorldZ(int worldZ) {
        return imageForOffset((long) worldZ - minWorldZ);
    }

    public int worldXForImageX(int imageX) {
        Objects.checkIndex(imageX, rasterSize);
        return minWorldX + sampleOffset(
                imageX,
                rasterSize,
                worldDiameter
        );
    }

    public int worldZForImageY(int imageY) {
        Objects.checkIndex(imageY, rasterSize);
        return minWorldZ + sampleOffset(
                imageY,
                rasterSize,
                worldDiameter
        );
    }

    private int imageForOffset(long offset) {
        if (offset < 0 || offset >= worldDiameter) {
            return -1;
        }
        return imageByWorldOffset[(int) offset];
    }

    private static int sampleOffset(
            int pixel,
            int rasterSize,
            int worldDiameter
    ) {
        double worldCoordinate =
                (pixel + 0.5) * worldDiameter / (double) rasterSize;
        return Math.min(
                worldDiameter - 1,
                Math.max(0, (int) Math.floor(worldCoordinate))
        );
    }

    private static int floorBlockCoordinate(double coordinate) {
        double floored = Math.floor(coordinate);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world coordinate is outside the supported block range"
            );
        }
        return (int) floored;
    }
}
