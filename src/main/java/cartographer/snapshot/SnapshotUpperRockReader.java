package cartographer.snapshot;

import cartographer.spatial.MapChunkPositionPlanner;
import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockCircleGeometry;
import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapAssembler;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.UpperRockTile;
import cartographer.snapshot.UpperRockTileLookup;
import cartographer.snapshot.WorldDataSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a complete request-shaped UPPER_ROCK map from revision-scoped snapshot tiles.
 *
 * <p>Any missing, corrupt, incompatible or semantically unsupported state is
 * a cache miss for snapshot-backed warm reads and must be handled by the caller's authoritative
 * source path.</p>
 */
public final class SnapshotUpperRockReader {
    private final RenderDataCacheStore cacheStore;
    private final MapChunkPositionPlanner planner = new MapChunkPositionPlanner();

    public SnapshotUpperRockReader(RenderDataCacheStore cacheStore) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public Optional<RockMap> read(
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

        RockCircleGeometry geometry = RockCircleGeometry.from(center, radius);
        if (!insideWorld(geometry, metadata)) {
            return Optional.empty();
        }

        Optional<WorldDataSnapshot> snapshot;
        try {
            snapshot = WorldDataSnapshot.openOrCreate(cacheStore, savePath);
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            return Optional.empty();
        }
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        List<MapChunkCoordinate> coordinates = planner.plan(
                metadata,
                geometry.centerX(),
                geometry.centerZ(),
                radius
        );

        RockCatalog catalog = RockCatalog.from(registry);
        if (catalog.rocks().isEmpty()) {
            return Optional.empty();
        }

        RockMapAssembler assembler = new RockMapAssembler(
                center,
                radius,
                0,
                metadata.mapSizeY(),
                RockMapMode.UPPER_ROCK,
                catalog
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
                                if (!acceptTile(
                                        lookup.tile(),
                                        geometry,
                                        catalog,
                                        assembler
                                )) {
                                    complete[0] = false;
                                    return false;
                                }
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
        return Optional.of(assembler.finish());
    }

    private boolean acceptTile(
            UpperRockTile tile,
            RockCircleGeometry geometry,
            RockCatalog catalog,
            RockMapAssembler assembler
    ) {
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
                if (!geometry.contains(worldX, worldZ)) {
                    continue;
                }

                RockColumnState state = tile.stateAt(localX, localZ);
                if (state == RockColumnState.OBSERVED) {
                    var identity = catalog.findByBlockId(
                            tile.blockIdAt(localX, localZ)
                    );
                    if (identity.isEmpty()) {
                        return false;
                    }
                    assembler.accept(RockColumnSample.observed(
                            worldX,
                            worldZ,
                            identity.orElseThrow(),
                            tile.rockYAt(localX, localZ)
                    ));
                } else if (state == RockColumnState.NO_ROCK) {
                    assembler.accept(
                            RockColumnSample.noRock(worldX, worldZ)
                    );
                } else {
                    assembler.accept(
                            RockColumnSample.unavailable(worldX, worldZ)
                    );
                }
            }
        }
        return true;
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
