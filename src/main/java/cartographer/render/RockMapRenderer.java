package cartographer.render;

import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockIdentity;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        long radiusSquared = (long) radius * radius;
        Map<RockIdentity, Long> rockCounts = new HashMap<>();
        long observedCount = 0;
        long noRockCount = 0;
        long unavailableCount = 0;

        for (RockColumnSample sample : rockMap.columns()) {
            int localX = sample.worldX() - minX;
            int localZ = sample.worldZ() - minZ;
            long dx = (long) sample.worldX() - centerX;
            long dz = (long) sample.worldZ() - centerZ;
            if (localX < 0 || localX >= diameter
                    || localZ < 0 || localZ >= diameter
                    || dx * dx + dz * dz > radiusSquared) {
                continue;
            }

            switch (sample.state()) {
                case OBSERVED -> {
                    image.setRGB(
                            localX,
                            localZ,
                            palette.colorFor(sample.rock().orElseThrow())
                    );
                    rockCounts.merge(sample.rock().orElseThrow(), 1L, Long::sum);
                    observedCount++;
                }
                case NO_ROCK -> {
                    image.setRGB(localX, localZ, NO_ROCK_COLOR);
                    noRockCount++;
                }
                case UNAVAILABLE -> {
                    int color = ((sample.worldX() + sample.worldZ()) & 1) == 0
                            ? UNAVAILABLE_LIGHT
                            : UNAVAILABLE_DARK;
                    image.setRGB(localX, localZ, color);
                    unavailableCount++;
                }
            }
        }

        List<RockLegendEntry> legend = new ArrayList<>();
        for (Map.Entry<RockIdentity, Long> entry : rockCounts.entrySet()) {
            legend.add(
                    new RockLegendEntry(
                            entry.getKey(),
                            palette.colorFor(entry.getKey()),
                            entry.getValue(),
                            observedCount == 0
                                    ? 0.0
                                    : entry.getValue() * 100.0 / observedCount
                    )
            );
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
                noRockCount,
                unavailableCount
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
