package cartographer.application;

import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldSnapshotPreparationSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectWorldSnapshotStatusUseCaseTest {

    @TempDir
    Path root;

    @Test
    void distinguishesNoSnapshotPartialAndReadyForCurrentRevision()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        InspectWorldSnapshotStatusUseCase useCase =
                new InspectWorldSnapshotStatusUseCase(cache);

        WorldSnapshotStatus initial = useCase.execute(save);
        assertEquals(
                WorldSnapshotStatus.State.NOT_PREPARED,
                initial.state()
        );
        assertTrue(
                cache.find(cache.observe(save)).isEmpty(),
                "status inspection must not create a cache manifest"
        );

        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();

        // A plain render-data manifest is not enough to claim PF-2 preparation.
        assertEquals(
                WorldSnapshotStatus.State.NOT_PREPARED,
                useCase.execute(save).state()
        );

        snapshot.preparationSummaryStore().publish(
                summary(snapshot.revisionHash(), false)
        );
        WorldSnapshotStatus partial = useCase.execute(save);
        assertEquals(WorldSnapshotStatus.State.PARTIAL, partial.state());
        assertTrue(partial.summary().isPresent());

        snapshot.preparationSummaryStore().publish(
                summary(snapshot.revisionHash(), true)
        );
        WorldSnapshotStatus ready = useCase.execute(save);
        assertEquals(WorldSnapshotStatus.State.READY, ready.state());
        assertTrue(ready.summary().orElseThrow().complete());
    }

    @Test
    void existingDerivedArtifactWithoutSummaryIsResumablePartial()
            throws Exception {
        Path save = root.resolve("legacy").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{9, 8, 7});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("legacy-cache"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();

        Files.createDirectories(
                snapshot.terrainStore().databasePath().getParent()
        );
        Files.write(
                snapshot.terrainStore().databasePath(),
                new byte[]{1}
        );

        WorldSnapshotStatus status =
                new InspectWorldSnapshotStatusUseCase(cache).execute(save);

        assertEquals(WorldSnapshotStatus.State.PARTIAL, status.state());
        assertTrue(status.summary().isEmpty());
    }

    @Test
    void changedSaveRevisionNeverReusesReadySummary() throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();
        snapshot.preparationSummaryStore().publish(
                summary(snapshot.revisionHash(), true)
        );

        InspectWorldSnapshotStatusUseCase useCase =
                new InspectWorldSnapshotStatusUseCase(cache);
        assertEquals(
                WorldSnapshotStatus.State.READY,
                useCase.execute(save).state()
        );

        // Revision identity also includes file size, so this is deterministic
        // without any scheduler or filesystem-timestamp waiting.
        Files.write(save, new byte[]{1, 2, 3, 4});

        WorldSnapshotStatus changed = useCase.execute(save);
        assertEquals(
                WorldSnapshotStatus.State.NOT_PREPARED,
                changed.state()
        );
        assertTrue(changed.summary().isEmpty());
    }

    private static WorldSnapshotPreparationSummary summary(
            String revision,
            boolean complete
    ) {
        return new WorldSnapshotPreparationSummary(
                revision,
                42,
                complete,
                complete,
                complete,
                complete,
                complete,
                complete
        );
    }
}
