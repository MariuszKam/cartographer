package cartographer.render;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockCircleGeometry;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.perf.UpperRockTile;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded snapshot-direct UPPER_ROCK renderer.
 *
 * <p>All request-circle cells contribute to counts and legend semantics, but
 * only world cells selected by the bounded raster sampling plan are painted.
 * No request-shaped RockMap is retained.</p>
 */
public final class RockSnapshotRenderAccumulator {
    private final WorldMetadata metadata;
    private final RockCatalog catalog;
    private final RockCircleGeometry circle;
    private final RockRenderSamplingPlan sampling;
    private final BufferedImage image;
    private final ArgbRaster raster;
    private final RockRenderColors colors;
    private final RockPalette palette;
    private final Map<Integer, Integer> ordinalByBlockId;
    private final long[] countsByOrdinal;
    private long observedCount;
    private long noRockCount;
    private long unavailableCount;
    private boolean finished;

    RockSnapshotRenderAccumulator(
            WorldMetadata metadata,
            RockCatalog catalog,
            WorldPosition center,
            int radius,
            RockPalette palette,
            int maxRasterSize
    ) {
        this.metadata = Objects.requireNonNull(
                metadata,
                "world metadata is required"
        );
        this.catalog = Objects.requireNonNull(
                catalog,
                "rock catalog is required"
        );
        Objects.requireNonNull(center, "center is required");
        if (catalog.rocks().isEmpty()) {
            throw new IllegalArgumentException(
                    "rock catalog cannot be empty"
            );
        }

        this.circle = RockCircleGeometry.from(center, radius);
        this.palette = Objects.requireNonNull(
                palette,
                "rock palette is required"
        );
        this.sampling = RockRenderSamplingPlan.from(
                center,
                radius,
                maxRasterSize
        );
        this.image = new BufferedImage(
                sampling.rasterSize(),
                sampling.rasterSize(),
                BufferedImage.TYPE_INT_ARGB
        );
        this.raster = ArgbRaster.wrap(image);
        this.colors = new RockRenderColors(this.palette);
        this.ordinalByBlockId = new HashMap<>();
        List<RockIdentity> rocks = catalog.rocks();
        this.countsByOrdinal = new long[rocks.size()];
        for (int ordinal = 0; ordinal < rocks.size(); ordinal++) {
            ordinalByBlockId.put(
                    rocks.get(ordinal).blockId(),
                    ordinal
            );
        }
    }

    public void accept(UpperRockTile tile) {
        ensureMutable();
        Objects.requireNonNull(tile, "UPPER_ROCK tile is required");
        if (!tile.matchesWorld(metadata)) {
            throw new IllegalArgumentException(
                    "UPPER_ROCK tile does not match world"
            );
        }

        int tileWorldX = Math.multiplyExact(
                tile.coordinate().x(),
                MapChunkCoordinate.SIZE_BLOCKS
        );
        int tileWorldZ = Math.multiplyExact(
                tile.coordinate().z(),
                MapChunkCoordinate.SIZE_BLOCKS
        );

        for (int localZ = 0; localZ < tile.height(); localZ++) {
            int worldZ = Math.addExact(tileWorldZ, localZ);
            for (int localX = 0; localX < tile.width(); localX++) {
                int worldX = Math.addExact(tileWorldX, localX);
                if (!circle.contains(worldX, worldZ)) {
                    continue;
                }

                RockColumnState state = tile.stateAt(localX, localZ);
                RockIdentity identity = null;
                switch (state) {
                    case OBSERVED -> {
                        int blockId = tile.blockIdAt(localX, localZ);
                        Integer ordinal = ordinalByBlockId.get(blockId);
                        if (ordinal == null) {
                            throw new IllegalArgumentException(
                                    "snapshot ROCK block is absent from catalog: "
                                            + blockId
                            );
                        }
                        identity = catalog.rocks().get(ordinal);
                        observedCount = Math.addExact(
                                observedCount,
                                1L
                        );
                        countsByOrdinal[ordinal] = Math.addExact(
                                countsByOrdinal[ordinal],
                                1L
                        );
                    }
                    case NO_ROCK -> noRockCount = Math.addExact(
                            noRockCount,
                            1L
                    );
                    case UNAVAILABLE -> unavailableCount = Math.addExact(
                            unavailableCount,
                            1L
                    );
                }

                int imageX = sampling.imageXForWorldX(worldX);
                int imageY = sampling.imageYForWorldZ(worldZ);
                if (imageX < 0 || imageY < 0) {
                    continue;
                }
                raster.setArgb(
                        imageX,
                        imageY,
                        colors.color(
                                state,
                                identity,
                                worldX,
                                worldZ,
                                Optional.empty()
                        )
                );
            }
        }
    }

    public RockMapRenderResult finish() {
        ensureMutable();
        finished = true;

        List<RockIdentity> rocks = catalog.rocks();
        java.util.ArrayList<RockLegendEntry> legend =
                new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < rocks.size(); ordinal++) {
            long count = countsByOrdinal[ordinal];
            if (count <= 0) {
                continue;
            }
            RockIdentity identity = rocks.get(ordinal);
            legend.add(new RockLegendEntry(
                    identity,
                    palette.colorFor(identity),
                    count,
                    observedCount == 0
                            ? 0.0
                            : count * 100.0 / observedCount
            ));
        }
        legend.sort(
                Comparator.comparingLong(
                                RockLegendEntry::observedCellCount
                        )
                        .reversed()
                        .thenComparing(entry -> entry.rock().code())
        );

        return new RockMapRenderResult(
                image,
                sampling.geometry(),
                legend,
                observedCount,
                noRockCount,
                unavailableCount
        );
    }

    private void ensureMutable() {
        if (finished) {
            throw new IllegalStateException(
                    "ROCK snapshot render accumulator is finished"
            );
        }
    }
}
