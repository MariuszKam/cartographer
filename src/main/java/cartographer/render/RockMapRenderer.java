package cartographer.render;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockIdentity;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RockMapRenderer {
    private final RockPalette palette;
    private final RockRenderColors colors;
    private final int maxRasterSize;

    public RockMapRenderer() {
        this(new RockPalette(), MapRasterContract.MAX_RASTER_SIZE);
    }

    public RockMapRenderer(RockPalette palette) {
        this(palette, MapRasterContract.MAX_RASTER_SIZE);
    }

    RockMapRenderer(RockPalette palette, int maxRasterSize) {
        this.palette = Objects.requireNonNull(palette, "rock palette is required");
        this.colors = new RockRenderColors(this.palette);
        if (maxRasterSize <= 0) {
            throw new IllegalArgumentException("maxRasterSize must be positive");
        }
        this.maxRasterSize = maxRasterSize;
    }

    public RockSnapshotRenderAccumulator snapshotAccumulator(
            cartographer.model.WorldMetadata metadata,
            cartographer.geology.rock.RockCatalog catalog,
            cartographer.model.WorldPosition center,
            int radius
    ) {
        return new RockSnapshotRenderAccumulator(
                metadata,
                catalog,
                center,
                radius,
                palette,
                maxRasterSize
        );
    }

    public RockMapRenderResult render(RockMap rockMap) {
        return render(rockMap, Optional.empty());
    }

    public RockMapRenderResult render(
            RockMap rockMap,
            Optional<String> highlightRockCode
    ) {
        Objects.requireNonNull(rockMap, "rock map is required");
        highlightRockCode = Objects.requireNonNull(
                highlightRockCode,
                "highlightRockCode is required"
        ).map(String::trim).filter(value -> !value.isEmpty());

        RockRenderSamplingPlan sampling =
                RockRenderSamplingPlan.from(
                        rockMap.center(),
                        rockMap.radius(),
                        maxRasterSize
                );
        int diameter = sampling.rasterSize();

        BufferedImage image = new BufferedImage(
                diameter,
                diameter,
                BufferedImage.TYPE_INT_ARGB
        );
        if (diameter == sampling.worldDiameter()) {
            drawOneToOne(
                    image,
                    rockMap,
                    sampling.minWorldX(),
                    sampling.minWorldZ(),
                    highlightRockCode
            );
        } else {
            drawSampled(
                    image,
                    rockMap,
                    sampling,
                    highlightRockCode
            );
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
                sampling.geometry(),
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
            int minZ,
            Optional<String> highlightRockCode
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
                        worldZ,
                        highlightRockCode
                );
            }
        }
    }

    private void drawSampled(
            BufferedImage image,
            RockMap rockMap,
            RockRenderSamplingPlan sampling,
            Optional<String> highlightRockCode
    ) {
        int raster = image.getWidth();
        for (int imageY = 0; imageY < raster; imageY++) {
            int worldZ = sampling.worldZForImageY(imageY);
            for (int imageX = 0; imageX < raster; imageX++) {
                int worldX = sampling.worldXForImageX(imageX);
                if (!rockMap.geometry().contains(worldX, worldZ)) {
                    continue;
                }
                int index = rockMap.geometry().cellIndex(worldX, worldZ);
                if (!rockMap.isPopulatedAtIndex(index)) {
                    continue;
                }
                paintCell(
                        image,
                        imageX,
                        imageY,
                        rockMap,
                        index,
                        worldX,
                        worldZ,
                        highlightRockCode
                );
            }
        }
    }

    private void paintCell(
            BufferedImage image,
            int imageX,
            int imageY,
            RockMap rockMap,
            int index,
            int worldX,
            int worldZ,
            Optional<String> highlightRockCode
    ) {
        RockColumnState state = rockMap.stateAtIndex(index);
        RockIdentity identity = state == RockColumnState.OBSERVED
                ? rockMap.ordinalTable()
                .get(rockMap.rockOrdinalAtIndex(index) - 1)
                : null;
        image.setRGB(
                imageX,
                imageY,
                colors.color(
                        state,
                        identity,
                        worldX,
                        worldZ,
                        highlightRockCode
                )
        );
    }

}
