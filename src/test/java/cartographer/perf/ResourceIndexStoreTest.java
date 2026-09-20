package cartographer.perf;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceIndexStoreTest {

    @TempDir
    Path root;

    @Test
    void roundTripsCoverageMembershipOccurrencesAndDetectsCorruption()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        ResourceIndexStore store = snapshot.resourceIndexStore();

        store.publishBlockCatalog(List.of(
                new BlockInfo(2, "game:ore-nativecopper-granite"),
                new BlockInfo(3, "game:ore-cassiterite-granite")
        ));

        ChunkPosition observed = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition empty = new ChunkPosition(2, 0, 2, 0);
        ChunkPosition missing = new ChunkPosition(3, 0, 2, 0);
        ChunkPosition failed = new ChunkPosition(4, 0, 2, 0);

        ResourceOccurrence copper = new ResourceOccurrence(
                observed,
                2,
                7,
                9,
                (1L << 2) | (1L << 5)
        );
        store.publish(List.of(
                ResourceChunkIndexEntry.available(
                        observed,
                        List.of(copper)
                ),
                ResourceChunkIndexEntry.available(
                        empty,
                        List.of()
                ),
                ResourceChunkIndexEntry.missing(missing),
                ResourceChunkIndexEntry.failed(failed)
        ));
        store.markScanComplete();

        assertEquals(
                Map.of(
                        2, "game:ore-nativecopper-granite",
                        3, "game:ore-cassiterite-granite"
                ),
                store.blockCatalog()
        );
        assertTrue(store.scanComplete());

        Map<ChunkPosition, ResourceChunkIndexLookup> coverage =
                store.readCoverage(
                        List.of(observed, empty, missing, failed)
                );
        assertEquals(
                ResourceChunkCoverageStatus.AVAILABLE,
                coverage.get(observed).coverageStatus()
        );
        assertEquals(
                ResourceChunkCoverageStatus.AVAILABLE,
                coverage.get(empty).coverageStatus()
        );
        assertEquals(
                ResourceChunkCoverageStatus.MISSING,
                coverage.get(missing).coverageStatus()
        );
        assertEquals(
                ResourceChunkCoverageStatus.FAILED,
                coverage.get(failed).coverageStatus()
        );

        assertEquals(
                Set.of(observed),
                store.positionsContainingAny(
                        List.of(observed, empty, missing, failed),
                        List.of(2)
                )
        );
        assertEquals(
                List.of(copper),
                store.readOccurrences(
                        List.of(observed, empty, missing, failed),
                        List.of(2)
                )
        );
        assertTrue(
                store.databasePath().startsWith(
                        root.resolve("cache").toAbsolutePath().normalize()
                )
        );

        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + store.databasePath().toUri()
        ); var statement = connection.prepareStatement(
                "UPDATE resource_chunk SET status = 'BROKEN' "
                        + "WHERE packed_position = ?"
        )) {
            statement.setLong(
                    1,
                    cartographer.save.ChunkPosEncoder.encode(observed)
            );
            statement.executeUpdate();
        }

        assertEquals(
                ResourceChunkIndexLookup.Status.CORRUPT,
                store.readCoverage(List.of(observed))
                        .get(observed)
                        .status()
        );
    }
}
