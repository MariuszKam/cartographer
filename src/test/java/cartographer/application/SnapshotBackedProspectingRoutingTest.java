package cartographer.application;

import cartographer.environment.EnvironmentProfile;
import cartographer.geology.rock.RockColumnState;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.IntDataMap2D;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.snapshot.MapRegionSnapshotEntry;
import cartographer.cache.RenderDataCacheStore;
import cartographer.index.ResourceChunkIndexEntry;
import cartographer.index.ResourceOccurrence;
import cartographer.snapshot.UpperRockTile;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotHeader;
import cartographer.prospecting.ActualOreObservation;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.SavedOreObservationProvider;
import cartographer.resource.ResourceAnalyzer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotBackedProspectingRoutingTest {
    @TempDir
    Path root;

    @Test
    void completeProspectingSnapshotWorksEvenWhenSourceIsNotSqlite()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        // Intentionally not a valid SQLite database. Any hidden source open
        // would make this test fail.
        Files.write(save, new byte[]{1, 2, 3, 4});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        prepareSnapshot(cache, save);

        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new SqliteSaveConnection(),
                reader,
                metadataReader
        );
        SavedOreObservationProvider provider =
                new SavedOreObservationProvider(
                        reader,
                        metadataReader,
                        sessionFactory,
                        cache
                );
        AnalyzeProspectingAreaUseCase useCase =
                new AnalyzeProspectingAreaUseCase(
                        reader,
                        new ResourceAnalyzer(),
                        OreRockCompatibilityProvider.unknown(),
                        provider,
                        sessionFactory,
                        cache
                );

        ProspectingAreaResult result = useCase.execute(
                new ProspectingAreaRequest(
                        save,
                        Optional.of(new WorldPosition(16, 0, 16)),
                        1,
                        List.of("copper")
                )
        );

        assertEquals(1, result.assessments().size());
        assertEquals(
                "copper",
                result.assessments().getFirst()
                        .candidate()
                        .resourceKey()
        );
        assertEquals(
                ActualOreObservation.OBSERVED,
                result.assessments().getFirst()
                        .candidate()
                        .evidence()
                        .actualOreObservation()
        );
        assertEquals(
                RockColumnState.OBSERVED,
                result.assessments().getFirst()
                        .candidate()
                        .evidence()
                        .geologyState()
        );
        assertEquals(1, result.rockMap().orElseThrow().observedCount());
    }

    private void prepareSnapshot(
            RenderDataCacheStore cache,
            Path save
    ) {
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(32, 64, 32);
        Map<Integer, BlockInfo> registry = Map.of(
                7,
                new BlockInfo(7, "game:rock-granite"),
                9,
                new BlockInfo(9, "game:ore-copper-native")
        );
        snapshot.headerStore().publish(new WorldSnapshotHeader(
                metadata,
                registry,
                Optional.of(new WorldPosition(16, 20, 16))
        ));

        MapRegionCoordinate regionCoordinate =
                new MapRegionCoordinate(0, 0);
        EnvironmentProfile environment = new EnvironmentProfile(
                regionCoordinate,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of()
        );
        snapshot.mapRegionStore().publish(List.of(
                new MapRegionSnapshotEntry(
                        regionCoordinate,
                        environment,
                        Optional.empty(),
                        Map.of(
                                "copper",
                                new IntDataMap2D(
                                        2,
                                        0,
                                        0,
                                        new int[]{1, 2, 3, 4}
                                )
                        )
                )
        ));
        snapshot.mapRegionStore().markScanComplete();

        int cells = 32 * 32;
        byte[] states = new byte[cells];
        int[] blockIds = new int[cells];
        int[] rockY = new int[cells];
        Arrays.fill(states, (byte) 2);
        Arrays.fill(blockIds, -1);
        Arrays.fill(rockY, -1);
        int center = 16 * 32 + 16;
        states[center] = 1;
        blockIds[center] = 7;
        rockY[center] = 22;
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

        snapshot.resourceIndexStore().publishBlockCatalog(
                List.of(registry.get(9))
        );
        List<ChunkPosition> positions = new OreChunkPositionPlanner().plan(
                metadata,
                16,
                16,
                1,
                ActualBlockYFilter.unbounded()
        );
        java.util.ArrayList<ResourceChunkIndexEntry> entries =
                new java.util.ArrayList<>();
        for (ChunkPosition position : positions) {
            entries.add(position.y() == 0
                    ? ResourceChunkIndexEntry.available(
                    position,
                    List.of(new ResourceOccurrence(
                            position,
                            9,
                            16,
                            16,
                            1L << 5
                    ))
            )
                    : ResourceChunkIndexEntry.available(
                    position,
                    List.of()
            ));
        }
        snapshot.resourceIndexStore().publish(entries);
    }
}
