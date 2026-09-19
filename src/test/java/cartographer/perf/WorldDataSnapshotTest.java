package cartographer.perf;

import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDataSnapshotTest {

    @Test
    void reopensSameRevisionAndReusesDerivedTerrainWithoutSourceMutation(
            @TempDir Path root
    ) throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        byte[] source = new byte[]{1, 2, 3, 4};
        Files.write(save, source);

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot first = WorldDataSnapshot.openOrCreate(cache, save)
                .orElseThrow();
        MapChunkCoordinate coordinate = new MapChunkCoordinate(2, 3);
        first.terrainStore().publish(List.of(new TerrainHeightTile(
                coordinate,
                true,
                true,
                new int[1024]
        )));

        WorldDataSnapshot second = WorldDataSnapshot.openOrCreate(cache, save)
                .orElseThrow();

        assertEquals(first.revisionHash(), second.revisionHash());
        assertEquals(
                TerrainTileLookup.Status.HIT,
                second.terrainStore()
                        .read(List.of(coordinate))
                        .get(coordinate)
                        .status()
        );
        assertTrue(
                second.terrainStore().databasePath()
                        .startsWith(root.resolve("cache").toAbsolutePath().normalize())
        );
        assertTrue(
                second.surfaceStore().databasePath()
                        .startsWith(root.resolve("cache").toAbsolutePath().normalize())
        );
        assertArrayEquals(source, Files.readAllBytes(save));
    }

    @Test
    void saveRevisionChangeCreatesDifferentSnapshotNamespace(
            @TempDir Path root
    ) throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot first = WorldDataSnapshot.openOrCreate(cache, save)
                .orElseThrow();

        Files.write(save, new byte[]{1, 2, 3, 4, 5});
        WorldDataSnapshot second = WorldDataSnapshot.openOrCreate(cache, save)
                .orElseThrow();

        assertNotEquals(first.revisionHash(), second.revisionHash());
        assertNotEquals(
                first.terrainStore().databasePath(),
                second.terrainStore().databasePath()
        );
    }
}
