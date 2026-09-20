package cartographer.snapshot;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.ResourceChunkIndexEntry;
import cartographer.perf.ResourceOccurrence;
import cartographer.perf.WorldDataSnapshot;
import cartographer.prospecting.ActualOreObservation;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotResourceReaderTest {
    @TempDir
    Path root;

    @Test
    void rebuildsExactOreMapAndObservationsFromOccurrenceMasks()
            throws Exception {
        Fixture fixture = fixture("resource");
        var positions = new OreChunkPositionPlanner().plan(
                fixture.metadata,
                16,
                16,
                4,
                ActualBlockYFilter.unbounded()
        );
        var entries = new java.util.ArrayList<ResourceChunkIndexEntry>();
        for (var position : positions) {
            if (position.y() == 0) {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of(new ResourceOccurrence(
                                position,
                                2,
                                16,
                                16,
                                1L << 5
                        ))
                ));
            } else {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of()
                ));
            }
        }
        fixture.snapshot.resourceIndexStore().publish(entries);

        SnapshotResourceReader reader =
                new SnapshotResourceReader(fixture.cache);
        var maps = reader.readMaps(
                fixture.save,
                fixture.metadata,
                fixture.registry,
                16,
                16,
                4,
                List.of(new ActualBlockMatchSpec(
                        "nativecopper",
                        ActualBlockMatchMode.ORE_CODE
                )),
                ActualBlockYFilter.unbounded()
        );

        assertTrue(maps.isPresent());
        var map = maps.orElseThrow().getFirst();
        assertEquals(1, map.matchingBlocks());
        assertEquals(1, map.hitColumns());
        assertEquals(5, map.minMatchedY());
        assertEquals(5, map.maxMatchedY());
        assertEquals(16, map.cells().getFirst().worldX());
        assertEquals(16, map.cells().getFirst().worldZ());

        var observations = reader.readObservations(
                fixture.save,
                fixture.metadata,
                fixture.registry,
                16,
                16,
                4,
                List.of("nativecopper", "cassiterite")
        ).orElseThrow();
        assertEquals(
                ActualOreObservation.OBSERVED,
                observations.get("nativecopper")
        );
        assertEquals(
                ActualOreObservation.NOT_OBSERVED,
                observations.get("cassiterite")
        );
    }

    @Test
    void prospectingIgnoresIndexedOccurrencesOutsideExactCircle()
            throws Exception {
        Fixture fixture = fixture("resource-circle");
        var positions = new OreChunkPositionPlanner().plan(
                fixture.metadata,
                16,
                16,
                4,
                ActualBlockYFilter.unbounded()
        );
        var entries = new java.util.ArrayList<ResourceChunkIndexEntry>();
        for (var position : positions) {
            if (position.y() == 0) {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of(new ResourceOccurrence(
                                position,
                                2,
                                0,
                                0,
                                1L << 5
                        ))
                ));
            } else {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of()
                ));
            }
        }
        fixture.snapshot.resourceIndexStore().publish(entries);

        var observations = new SnapshotResourceReader(fixture.cache)
                .readObservations(
                        fixture.save,
                        fixture.metadata,
                        fixture.registry,
                        16,
                        16,
                        4,
                        List.of("nativecopper")
                )
                .orElseThrow();

        assertEquals(
                ActualOreObservation.NOT_OBSERVED,
                observations.get("nativecopper")
        );
    }

    @Test
    void missingRequestCoverageFallsBackToSource() throws Exception {
        Fixture fixture = fixture("resource-miss");
        SnapshotResourceReader reader =
                new SnapshotResourceReader(fixture.cache);

        assertTrue(reader.readMaps(
                fixture.save,
                fixture.metadata,
                fixture.registry,
                48,
                48,
                4,
                List.of(new ActualBlockMatchSpec(
                        "nativecopper",
                        ActualBlockMatchMode.ORE_CODE
                )),
                ActualBlockYFilter.unbounded()
        ).isEmpty());
    }

    @Test
    void genericContainsMatchIsNotClaimedByOreIndex() throws Exception {
        Fixture fixture = fixture("resource-generic");
        SnapshotResourceReader reader =
                new SnapshotResourceReader(fixture.cache);

        assertTrue(reader.readMaps(
                fixture.save,
                fixture.metadata,
                fixture.registry,
                16,
                16,
                4,
                List.of(new ActualBlockMatchSpec(
                        "copper",
                        ActualBlockMatchMode.CONTAINS
                )),
                ActualBlockYFilter.unbounded()
        ).isEmpty());
    }

    private Fixture fixture(String name) throws Exception {
        Path save = root.resolve(name).resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve(name + "-cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        Map<Integer, BlockInfo> registry = Map.of(
                2,
                new BlockInfo(
                        2,
                        "game:ore-nativecopper-granite"
                )
        );
        snapshot.resourceIndexStore().publishBlockCatalog(
                registry.values()
        );
        return new Fixture(
                save,
                cache,
                snapshot,
                new WorldMetadata(64, 64, 64),
                registry
        );
    }

    private record Fixture(
            Path save,
            RenderDataCacheStore cache,
            WorldDataSnapshot snapshot,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry
    ) {
    }
}
