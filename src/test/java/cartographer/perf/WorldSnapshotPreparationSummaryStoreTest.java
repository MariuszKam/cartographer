package cartographer.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSnapshotPreparationSummaryStoreTest {

    @TempDir
    Path root;

    @Test
    void roundTripsRevisionScopedPreparationSummary() throws Exception {
        Path save = root.resolve("world.vcdbs");
        Files.write(save, new byte[]{4, 5, 6});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();

        WorldSnapshotPreparationSummary expected =
                new WorldSnapshotPreparationSummary(
                        snapshot.revisionHash(),
                        17,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                );
        snapshot.preparationSummaryStore().publish(expected);

        assertEquals(
                expected,
                snapshot.preparationSummaryStore()
                        .read()
                        .orElseThrow()
        );
        assertTrue(expected.complete());
    }
}
