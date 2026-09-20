package cartographer.perf;

import cartographer.testing.IntegrationTest;
import cartographer.environment.ClimateSummary;
import cartographer.environment.EnvironmentLabel;
import cartographer.environment.EnvironmentProfile;
import cartographer.environment.ForestDensityClass;
import cartographer.environment.ForestSummary;
import cartographer.environment.IdMapSummary;
import cartographer.environment.OceanSummary;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.MapRegionCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class MapRegionSnapshotStoreTest {

    @TempDir
    Path root;

    @Test
    void persistsInterpretedStateAndDetectsCorruption() throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{4, 5, 6});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        MapRegionSnapshotStore store = snapshot.mapRegionStore();

        MapRegionCoordinate coordinate = new MapRegionCoordinate(2, 3);
        EnvironmentProfile profile = new EnvironmentProfile(
                coordinate,
                Optional.of(new ClimateSummary(
                        4,
                        90.5,
                        170.25,
                        List.of(1, 2, 3, 4)
                )),
                Optional.of(new ForestSummary(
                        4,
                        0,
                        255,
                        0.5,
                        ForestDensityClass.MODERATE
                )),
                Optional.of(new OceanSummary(
                        4,
                        0,
                        10,
                        2.5
                )),
                Optional.of(new IdMapSummary(
                        4,
                        2,
                        List.of(8, 9)
                )),
                Optional.of(new IdMapSummary(
                        4,
                        2,
                        List.of(11, 12)
                )),
                Set.of(EnvironmentLabel.HUMID)
        );
        MapRegionSnapshotEntry entry = new MapRegionSnapshotEntry(
                coordinate,
                profile,
                Optional.of(new GeologicProvinceSummary(
                        coordinate,
                        4,
                        2,
                        List.of(11, 12)
                ))
        );

        store.publish(List.of(entry));
        assertFalse(store.scanComplete());
        store.markScanComplete();

        MapRegionSnapshotRead read = store.readAll();
        assertTrue(read.healthyComplete());
        assertEquals(List.of(entry), read.entries());
        assertTrue(store.databasePath().startsWith(
                root.resolve("cache").toAbsolutePath().normalize()
        ));

        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + store.databasePath().toUri()
        ); var statement = connection.prepareStatement(
                "UPDATE mapregion_snapshot SET payload = ? "
                        + "WHERE region_x = 2 AND region_z = 3"
        )) {
            statement.setBytes(1, new byte[]{9, 9});
            statement.executeUpdate();
        }

        MapRegionSnapshotRead corrupt = store.readAll();
        assertTrue(corrupt.scanComplete());
        assertEquals(1, corrupt.corruptRows());
        assertFalse(corrupt.healthyComplete());
    }
}
