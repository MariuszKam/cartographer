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

    public RockMapRenderer() {
        this(new RockPalette());
    }

    public RockMapRenderer(RockPalette palette) {
        this.palette = Objects.requireNonNull(palette, "rock palette is required");
    }

    public RockMapRenderResult render(RockMap rockMap) {
        Objects.requireNonNull(rockMap, "rock map is required");

        int radius = rockMap.radius();
        int diameter;
        try {
            diameter = Math.addExact(Math.multiplyExact(radius, 2), 1);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("rock map image is too large", exception);
        }

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
                minX + diameter, minZ + diameter
        );
        for (int row = 0; row < rockMap.geometry().rowCount(); row++) {
            int worldZ = rockMap.geometry().worldZForRow(row);
            int rowStartX = rockMap.geometry().rowStartX(row);
            long rowOffset = rockMap.geometry().rowOffset(row);
            for (int offset = 0; offset < rockMap.geometry().rowLength(row); offset++) {
                int index = Math.toIntExact(rowOffset + offset);
                if (!rockMap.isPopulatedAtIndex(index)) continue;
                int worldX = Math.addExact(rowStartX, offset);
                int localX = Math.subtractExact(worldX, minX);
                int localZ = Math.subtractExact(worldZ, minZ);
                switch (rockMap.stateAtIndex(index)) {
                    case OBSERVED -> {
                        RockIdentity identity = rockMap.ordinalTable()
                                .get(rockMap.rockOrdinalAtIndex(index) - 1);
                        image.setRGB(localX, localZ, palette.colorFor(identity));
                    }
                    case NO_ROCK -> image.setRGB(localX, localZ, NO_ROCK_COLOR);
                    case UNAVAILABLE -> image.setRGB(
                            localX,
                            localZ,
                            ((worldX + worldZ) & 1) == 0
                                    ? UNAVAILABLE_LIGHT
                                    : UNAVAILABLE_DARK
                    );
                }
            }
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
