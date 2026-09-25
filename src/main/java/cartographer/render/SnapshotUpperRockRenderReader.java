package cartographer.render;

import cartographer.spatial.MapChunkPositionPlanner;
import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockCircleGeometry;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.UpperRockTileLookup;
import cartographer.snapshot.WorldDataSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot-direct UPPER_ROCK render path.
 *
 * <p>Complete tile coverage is consumed in bounded batches directly into the
 * raster and aggregate legend/count state. No request-shaped RockMap is built
 * or retained.</p>
 */
public final class SnapshotUpperRockRenderReader {
    private final RenderDataCacheStore cacheStore;
    private final RockMapRenderer renderer;
    private final MapChunkPositionPlanner planner =
            new MapChunkPositionPlanner();

    public SnapshotUpperRockRenderReader(
            RenderDataCacheStore cacheStore,
            RockMapRenderer renderer
    ) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
        this.renderer = Objects.requireNonNull(
                renderer,
                "rock renderer is required"
        );
    }

    public Optional<RockMapRenderResult> read(
            Path savePath,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            WorldPosition center,
            int radius
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }

        RockCircleGeometry geometry =
                RockCircleGeometry.from(center, radius);
        if (!insideWorld(geometry, metadata)) {
            return Optional.empty();
        }

        Optional<WorldDataSnapshot> snapshot;
        try {
            snapshot = WorldDataSnapshot.openOrCreate(
                    cacheStore,
                    savePath
            );
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            return Optional.empty();
        }
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        RockCatalog catalog = RockCatalog.from(registry);
        if (catalog.rocks().isEmpty()) {
            return Optional.empty();
        }

        List<MapChunkCoordinate> coordinates = planner.plan(
                metadata,
                geometry.centerX(),
                geometry.centerZ(),
                radius
        );
        RockSnapshotRenderAccumulator accumulator =
                renderer.snapshotAccumulator(
                        metadata,
                        catalog,
                        center,
                        radius
                );
        boolean[] complete = {true};

        try {
            snapshot.orElseThrow()
                    .upperRockTileStore()
                    .forEachLookup(
                            coordinates,
                            (coordinate, lookup) -> {
                                if (lookup.status()
                                        != UpperRockTileLookup.Status.HIT
                                        || !lookup.tile()
                                        .matchesWorld(metadata)) {
                                    complete[0] = false;
                                    return false;
                                }
                                accumulator.accept(lookup.tile());
                                return true;
                            }
                    );
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            return Optional.empty();
        }

        if (!complete[0]) {
            return Optional.empty();
        }
        return Optional.of(accumulator.finish());
    }

    private boolean insideWorld(
            RockCircleGeometry geometry,
            WorldMetadata metadata
    ) {
        long minX = (long) geometry.centerX() - geometry.radius();
        long maxX = (long) geometry.centerX() + geometry.radius();
        long minZ = (long) geometry.centerZ() - geometry.radius();
        long maxZ = (long) geometry.centerZ() + geometry.radius();
        return minX >= 0
                && minZ >= 0
                && maxX < metadata.mapSizeX()
                && maxZ < metadata.mapSizeZ();
    }
}
