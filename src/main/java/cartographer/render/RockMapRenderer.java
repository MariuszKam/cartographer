package cartographer.render;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockIdentity;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RockMapRenderer {
    private static final int NO_ROCK_COLOR = 0xFF4A4A4A;
    private static final int UNAVAILABLE_LIGHT = 0xFF888888;
    private static final int UNAVAILABLE_DARK = 0xFF707070;

    private final RockPalette palette;
    private final int maxRasterSize;

    public RockMapRenderer() {
        this(new RockPalette(), MapRasterContract.MAX_RASTER_SIZE);
    }

    public RockMapRenderer(RockPalette palette) {
        this(palette, MapRasterContract.MAX_RASTER_SIZE);
    }

    RockMapRenderer(RockPalette palette, int maxRasterSize) {
        this.palette = Objects.requireNonNull(palette, "rock palette is required");
        if (maxRasterSize <= 0) {
            throw new IllegalArgumentException("maxRasterSize must be positive");
        }
        this.maxRasterSize = maxRasterSize;
    }

    public RockMapRenderResult render(RockMap rockMap) {
        Objects.requireNonNull(rockMap, "rock map is required");

        int radius = rockMap.radius();
        int worldDiameter;
        try {
            worldDiameter = Math.addExact(Math.multiplyExact(radius, 2), 1);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("rock map image is too large", exception);
        }
        int diameter = Math.min(worldDiameter, maxRasterSize);

        BufferedImage image = new BufferedImage(
                diameter,
                diameter,
                BufferedImage.TYPE_INT_ARGB
        );
        int centerX = floorBlockCoordinate(rockMap.center().x());
        int centerZ = floorBlockCoordinate(rockMap.center().z());
        int minX = centerX - radius;
        int minZ = centerZ - radius;
        MapViewportGeometry geometry = MapViewportGeometry.fullImage(
                diameter, diameter, minX, minZ,
                minX + (double) worldDiameter,
                minZ + (double) worldDiameter
        );
        if (diameter == worldDiameter) {
            drawOneToOne(image, rockMap, minX, minZ);
        } else {
            drawSampled(image, rockMap, minX, minZ, worldDiameter);
        }

        long observedCount = rockMap.observedCount();
        List<RockLegendEntry> legend = new ArrayList<>();
        long[] countsByOrdinal = rockMap.countsByOrdinal();
        List<RockIdentity> ordinalTable = rockMap.ordinalTable();
        for (int ordinal = 1; ordinal <= ordinalTable.size(); ordinal++) {
            long count = countsByOrdinal[ordinal];
            if (count <= 0) continue;
            RockIdentity identity = ordinalTable.get(ordinal - 1);
            legend.add(new RockLegendEntry(
                    identity,
                    palette.colorFor(identity),
                    count,
                    observedCount == 0 ? 0.0 : count * 100.0 / observedCount
            ));
        }
        legend.sort(
                Comparator.comparingLong(RockLegendEntry::observedCellCount)
                        .reversed()
                        .thenComparing(entry -> entry.rock().code())
        );

        return new RockMapRenderResult(
                image,
                geometry,
                legend,
                observedCount,
                rockMap.noRockCount(),
                rockMap.unavailableCount()
        );
    }

    private void drawOneToOne(
            BufferedImage image,
            RockMap rockMap,
            int minX,
            int minZ
    ) {
        for (int row = 0; row < rockMap.geometry().rowCount(); row++) {
            int worldZ = rockMap.geometry().worldZForRow(row);
            int rowStartX = rockMap.geometry().rowStartX(row);
            long rowOffset = rockMap.geometry().rowOffset(row);
            for (int offset = 0; offset < rockMap.geometry().rowLength(row); offset++) {
                int index = Math.toIntExact(rowOffset + offset);
                if (!rockMap.isPopulatedAtIndex(index)) continue;
                int worldX = Math.addExact(rowStartX, offset);
                paintCell(
                        image,
                        Math.subtractExact(worldX, minX),
                        Math.subtractExact(worldZ, minZ),
                        rockMap,
                        index,
                        worldX,
                        worldZ
                );
            }
        }
    }

    private void drawSampled(
            BufferedImage image,
            RockMap rockMap,
            int minX,
            int minZ,
            int worldDiameter
    ) {
        int raster = image.getWidth();
        for (int imageY = 0; imageY < raster; imageY++) {
            int worldZ = minZ + sampleOffset(imageY, raster, worldDiameter);
            for (int imageX = 0; imageX < raster; imageX++) {
                int worldX = minX + sampleOffset(imageX, raster, worldDiameter);
                if (!rockMap.geometry().contains(worldX, worldZ)) {
                    continue;
                }
                int index = rockMap.geometry().cellIndex(worldX, worldZ);
                if (!rockMap.isPopulatedAtIndex(index)) {
                    continue;
                }
                paintCell(image, imageX, imageY, rockMap, index, worldX, worldZ);
            }
        }
    }

    private int sampleOffset(int pixel, int rasterSize, int worldDiameter) {
        double worldCoordinate =
                (pixel + 0.5) * worldDiameter / (double) rasterSize;
        return Math.min(
                worldDiameter - 1,
                Math.max(0, (int) Math.floor(worldCoordinate))
        );
    }

    private void paintCell(
            BufferedImage image,
            int imageX,
            int imageY,
            RockMap rockMap,
            int index,
            int worldX,
            int worldZ
    ) {
        switch (rockMap.stateAtIndex(index)) {
            case OBSERVED -> {
                RockIdentity identity = rockMap.ordinalTable()
                        .get(rockMap.rockOrdinalAtIndex(index) - 1);
                image.setRGB(imageX, imageY, palette.colorFor(identity));
            }
            case NO_ROCK -> image.setRGB(imageX, imageY, NO_ROCK_COLOR);
            case UNAVAILABLE -> image.setRGB(
                    imageX,
                    imageY,
                    ((worldX + worldZ) & 1) == 0
                            ? UNAVAILABLE_LIGHT
                            : UNAVAILABLE_DARK
            );
        }
    }

    private int floorBlockCoordinate(double coordinate) {
        double floored = Math.floor(coordinate);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world coordinate is outside the supported block range"
            );
        }
        return (int) floored;
    }
}
