package cartographer.snapshot;

import cartographer.cache.RenderDataCacheStore;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSnapshotHeaderStoreTest {
    @TempDir
    Path root;

    @Test
    void roundTripsHeaderAndIsolatesItBySaveRevision() throws Exception {
        Path save = root.resolve("world.vcdbs");
        Files.write(save, new byte[]{1, 2, 3});
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot first =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        WorldSnapshotHeader header = new WorldSnapshotHeader(
                new WorldMetadata(64, 256, 96),
                Map.of(
                        0, new BlockInfo(0, "game:air"),
                        7, new BlockInfo(7, "game:rock-granite")
                ),
                Optional.of(new WorldPosition(12.5, 81, 18.25))
        );
        first.headerStore().publish(header);

        assertEquals(header, first.headerStore().read().orElseThrow());

        Files.write(
                save,
                new byte[]{4},
                StandardOpenOption.APPEND
        );
        WorldDataSnapshot second =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        assertTrue(second.headerStore().read().isEmpty());
    }

    @Test
    void corruptHeaderIsARecoverableSnapshotMiss() throws Exception {
        Path save = root.resolve("corrupt.vcdbs");
        Files.write(save, new byte[]{1});
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("corrupt-cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        snapshot.headerStore().publish(new WorldSnapshotHeader(
                new WorldMetadata(32, 64, 32),
                Map.of(1, new BlockInfo(1, "game:rock-granite")),
                Optional.of(new WorldPosition(16, 20, 16))
        ));

        Files.write(
                snapshot.headerStore().path(),
                new byte[]{9, 8, 7}
        );

        assertTrue(snapshot.headerStore().read().isEmpty());
    }
}
