package cartographer.snapshot;

import cartographer.application.MapChunkPositionPlanner;
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
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.UpperRockTile;
import cartographer.perf.UpperRockTileLookup;
import cartographer.perf.WorldDataSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a complete request-shaped UPPER_ROCK map from PF-2.4 snapshot tiles.
 *
 * <p>Any missing, corrupt, incompatible or semantically unsupported state is
 * a cache miss for PF-2.6 and must be handled by the caller's authoritative
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
        Map<MapChunkCoordinate, UpperRockTileLookup> lookups;
        try {
            lookups = snapshot.orElseThrow()
                    .upperRockTileStore()
                    .read(coordinates);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }

        for (MapChunkCoordinate coordinate : coordinates) {
            UpperRockTileLookup lookup = lookups.get(coordinate);
            if (lookup == null
                    || lookup.status() != UpperRockTileLookup.Status.HIT
                    || !lookup.tile().matchesWorld(metadata)) {
                return Optional.empty();
            }
        }

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

        for (int row = 0; row < geometry.rowCount(); row++) {
            int worldZ = geometry.worldZForRow(row);
            int startX = geometry.rowStartX(row);
            int length = geometry.rowLength(row);
            for (int offset = 0; offset < length; offset++) {
                int worldX = Math.addExact(startX, offset);
                MapChunkCoordinate coordinate = new MapChunkCoordinate(
                        Math.floorDiv(
                                worldX,
                                MapChunkCoordinate.SIZE_BLOCKS
                        ),
                        Math.floorDiv(
                                worldZ,
                                MapChunkCoordinate.SIZE_BLOCKS
                        )
                );
                UpperRockTileLookup lookup = lookups.get(coordinate);
                if (lookup == null
                        || lookup.status() != UpperRockTileLookup.Status.HIT) {
                    return Optional.empty();
                }
                UpperRockTile tile = lookup.tile();
                int localX = Math.floorMod(
                        worldX,
                        MapChunkCoordinate.SIZE_BLOCKS
                );
                int localZ = Math.floorMod(
                        worldZ,
                        MapChunkCoordinate.SIZE_BLOCKS
                );
                RockColumnState state = tile.stateAt(localX, localZ);
                if (state == RockColumnState.OBSERVED) {
                    var identity = catalog.findByBlockId(
                            tile.blockIdAt(localX, localZ)
                    );
                    if (identity.isEmpty()) {
                        return Optional.empty();
                    }
                    assembler.accept(RockColumnSample.observed(
                            worldX,
                            worldZ,
                            identity.orElseThrow(),
                            tile.rockYAt(localX, localZ)
                    ));
                } else if (state == RockColumnState.NO_ROCK) {
                    assembler.accept(RockColumnSample.noRock(worldX, worldZ));
                } else {
                    assembler.accept(
                            RockColumnSample.unavailable(worldX, worldZ)
                    );
                }
            }
        }
        return Optional.of(assembler.finish());
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
