package cartographer.snapshot;

import cartographer.cache.RenderDataCacheStore;

import cartographer.testing.IntegrationTest;
import cartographer.geology.rock.RockColumnState;
import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class UpperRockTileStoreTest {

    @TempDir
    Path root;

    @Test
    void boundedVisitorPreservesOrderAndCanStopEarly() throws Exception {
        Path save = root.resolve("visitor-save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("visitor-cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        UpperRockTileStore store = snapshot.upperRockTileStore();

        int cells = 32 * 32;
        byte[] states = new byte[cells];
        int[] blockIds = new int[cells];
        int[] rockY = new int[cells];
        java.util.Arrays.fill(
                states,
                UpperRockTile.encodeState(RockColumnState.NO_ROCK)
        );
        java.util.Arrays.fill(blockIds, -1);
        java.util.Arrays.fill(rockY, -1);
        MapChunkCoordinate hit = new MapChunkCoordinate(0, 0);
        store.publish(List.of(new UpperRockTile(
                hit,
                64,
                64,
                32,
                32,
                32,
                states,
                blockIds,
                rockY
        )));

        MapChunkCoordinate miss = new MapChunkCoordinate(1, 0);
        List<MapChunkCoordinate> visited = new ArrayList<>();
        List<UpperRockTileLookup.Status> statuses = new ArrayList<>();
        boolean exhausted = store.forEachLookup(
                List.of(hit, miss, hit),
                (coordinate, lookup) -> {
                    visited.add(coordinate);
                    statuses.add(lookup.status());
                    return visited.size() < 2;
                }
        );

        assertFalse(exhausted);
        assertEquals(List.of(hit, miss), visited);
        assertEquals(
                List.of(
                        UpperRockTileLookup.Status.HIT,
                        UpperRockTileLookup.Status.MISS
                ),
                statuses
        );
    }

    @Test
    void roundTripsAndRejectsCorruptDerivedTile() throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        UpperRockTileStore store = snapshot.upperRockTileStore();

        int cells = 32 * 32;
        byte[] states = new byte[cells];
        int[] blockIds = new int[cells];
        int[] rockY = new int[cells];
        java.util.Arrays.fill(
                states,
                UpperRockTile.encodeState(RockColumnState.NO_ROCK)
        );
        java.util.Arrays.fill(blockIds, -1);
        java.util.Arrays.fill(rockY, -1);
        states[0] = UpperRockTile.encodeState(RockColumnState.OBSERVED);
        blockIds[0] = 7;
        rockY[0] = 22;

        UpperRockTile tile = new UpperRockTile(
                new MapChunkCoordinate(0, 0),
                32,
                64,
                32,
                32,
                32,
                states,
                blockIds,
                rockY
        );
        store.publish(List.of(tile));

        UpperRockTileLookup lookup = store.read(
                List.of(new MapChunkCoordinate(0, 0))
        ).get(new MapChunkCoordinate(0, 0));
        assertEquals(UpperRockTileLookup.Status.HIT, lookup.status());
        assertEquals(RockColumnState.OBSERVED, lookup.tile().stateAt(0, 0));
        assertEquals(22, lookup.tile().rockYAt(0, 0));
        assertTrue(store.databasePath().startsWith(
                root.resolve("cache").toAbsolutePath().normalize()
        ));

        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + store.databasePath().toUri()
        ); var statement = connection.prepareStatement(
                "UPDATE upper_rock_tile SET payload = ? "
                        + "WHERE mapchunk_x = 0 AND mapchunk_z = 0"
        )) {
            statement.setBytes(1, new byte[]{1, 2, 3});
            statement.executeUpdate();
        }

        assertEquals(
                UpperRockTileLookup.Status.CORRUPT,
                store.read(List.of(new MapChunkCoordinate(0, 0)))
                        .get(new MapChunkCoordinate(0, 0))
                        .status()
        );
    }
}
