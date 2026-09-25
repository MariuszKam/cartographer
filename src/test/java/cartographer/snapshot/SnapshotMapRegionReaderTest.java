package cartographer.snapshot;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.MapRegionCoordinate;
import cartographer.snapshot.MapRegionSnapshotEntry;
import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.WorldDataSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotMapRegionReaderTest {
    @TempDir
    Path root;

    @Test
    void exposesOnlyHealthyCompleteMapregionSnapshot() throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        MapRegionCoordinate coordinate = new MapRegionCoordinate(2, 3);
        EnvironmentProfile environment = new EnvironmentProfile(
                coordinate,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of()
        );
        GeologicProvinceSummary geology =
                new GeologicProvinceSummary(
                        coordinate,
                        4,
                        2,
                        List.of(7, 8)
                );
        snapshot.mapRegionStore().publish(List.of(
                new MapRegionSnapshotEntry(
                        coordinate,
                        environment,
                        Optional.of(geology)
                )
        ));

        SnapshotMapRegionReader reader =
                new SnapshotMapRegionReader(cache);
        assertTrue(reader.read(save).isEmpty());

        snapshot.mapRegionStore().markScanComplete();
        var result = reader.read(save).orElseThrow();
        assertEquals(List.of(environment), result.environmentProfiles());
        assertEquals(List.of(geology), result.geologySummaries());

        snapshot.mapRegionStore().markScanIncomplete();
        assertTrue(reader.read(save).isEmpty());
    }
}
