package cartographer.snapshot;

import cartographer.geology.rock.RockColumnState;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.UpperRockTile;
import cartographer.perf.WorldDataSnapshot;
import cartographer.render.RockPalette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotUpperRockRenderReaderTest {
    @TempDir
    Path root;

    @Test
    void rendersCompleteSnapshotWithoutBuildingRockMap() throws Exception {
        Path save = root.resolve("rock").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        int cells = 32 * 32;
        byte[] states = new byte[cells];
        int[] blockIds = new int[cells];
        int[] rockY = new int[cells];
        Arrays.fill(states, (byte) 2);
        Arrays.fill(blockIds, -1);
        Arrays.fill(rockY, -1);
        int centerIndex = 16 * 32 + 16;
        states[centerIndex] = 1;
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

        var result = new SnapshotUpperRockRenderReader(cache).read(
                save,
                new WorldMetadata(32, 64, 32),
                Map.of(7, new BlockInfo(7, "game:rock-granite")),
                new WorldPosition(16, 0, 16),
                1
        );

        assertTrue(result.isPresent());
        var rendered = result.orElseThrow();
        assertEquals(1, rendered.observedCount());
        assertEquals(4, rendered.noRockCount());
        assertEquals(
                new RockPalette().colorFor(
                        rendered.legend().getFirst().rock()
                ),
                rendered.image().getRGB(1, 1)
        );
    }
}
