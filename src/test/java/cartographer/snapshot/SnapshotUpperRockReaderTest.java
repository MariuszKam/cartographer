package cartographer.snapshot;

import cartographer.geology.rock.RockColumnState;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.UpperRockTile;
import cartographer.snapshot.WorldDataSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotUpperRockReaderTest {
    @TempDir
    Path root;

    @Test
    void reconstructsRequestShapedRockMapFromCompleteTile() throws Exception {
        Path save = save("rock");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-rock"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        int cells = 32 * 32;
        byte[] states = new byte[cells];
        int[] blockIds = new int[cells];
        int[] rockY = new int[cells];
        Arrays.fill(states, (byte) 2); // NO_ROCK
        Arrays.fill(blockIds, -1);
        Arrays.fill(rockY, -1);
        int centerIndex = 16 * 32 + 16;
        states[centerIndex] = 1; // OBSERVED
        blockIds[centerIndex] = 7;
        rockY[centerIndex] = 22;
        snapshot.upperRockTileStore().publish(List.of(
                new UpperRockTile(
                        new MapChunkCoordinate(0, 0),
                        32,
                        64,
                        32,
                        32,
                        32,
                        states,
                        blockIds,
                        rockY
                )
        ));

        var result = new SnapshotUpperRockReader(cache).read(
                save,
                new WorldMetadata(32, 64, 32),
                Map.of(7, new BlockInfo(7, "game:rock-granite")),
                new WorldPosition(16, 0, 16),
                1
        );

        assertTrue(result.isPresent());
        var map = result.orElseThrow();
        assertEquals(1, map.observedCount());
        assertEquals(4, map.noRockCount());
        assertEquals(
                RockColumnState.OBSERVED,
                map.sampleAt(16, 16).orElseThrow().state()
        );
        assertEquals(
                22,
                map.sampleAt(16, 16).orElseThrow().rockY().orElseThrow()
        );
    }

    @Test
    void returnsMissWhenAnyRequiredTileIsAbsent() throws Exception {
        Path save = save("rock-miss");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-rock-miss"));
        WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        assertTrue(new SnapshotUpperRockReader(cache).read(
                save,
                new WorldMetadata(96, 64, 96),
                Map.of(7, new BlockInfo(7, "game:rock-granite")),
                new WorldPosition(48, 0, 48),
                8
        ).isEmpty());
    }

    private Path save(String name) throws Exception {
        Path save = root.resolve(name).resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});
        return save;
    }
}
