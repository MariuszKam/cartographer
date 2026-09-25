package cartographer.prospecting;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.application.ProgressReporter;
import cartographer.geology.rock.RockColumnState;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.cache.RenderDataCacheStore;
import cartographer.index.ResourceChunkIndexEntry;
import cartographer.index.ResourceOccurrence;
import cartographer.snapshot.UpperRockTile;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedOreObservationProviderSnapshotTest {
    @TempDir
    Path root;

    @Test
    void completeRockAndResourceSnapshotSkipsFusedSourceTraversal()
            throws Exception {
        Path save = save("hit");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-hit"));
        publishRockSnapshot(cache, save);
        publishResourceSnapshot(cache, save);

        CountingReader reader = new CountingReader();
        MetadataReader metadataReader = new MetadataReader();
        SaveSessionFactory sessions = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        SavedOreObservationProvider provider =
                new SavedOreObservationProvider(
                        reader,
                        metadataReader,
                        cache
                );

        FusedProspectingResult result;
        try (SaveSession session = sessions.open(save)) {
            result = provider.analyze(
                    session,
                    new WorldPosition(16, 0, 16),
                    1,
                    List.of("copper")
            );
        }

        assertEquals(0, reader.sourceCalls.get());
        assertEquals(
                ActualOreObservation.OBSERVED,
                result.observation("copper")
        );
        assertEquals(1, result.rockMap().observedCount());
        assertEquals(
                RockColumnState.OBSERVED,
                result.rockMap()
                        .sampleAt(16, 16)
                        .orElseThrow()
                        .state()
        );
    }

    @Test
    void incompleteResourceSnapshotFallsBackToOneFusedSourceTraversal()
            throws Exception {
        Path save = save("fallback");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-fallback"));
        publishRockSnapshot(cache, save);

        CountingReader reader = new CountingReader();
        MetadataReader metadataReader = new MetadataReader();
        SaveSessionFactory sessions = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        SavedOreObservationProvider provider =
                new SavedOreObservationProvider(
                        reader,
                        metadataReader,
                        cache
                );

        try (SaveSession session = sessions.open(save)) {
            provider.analyze(
                    session,
                    new WorldPosition(16, 0, 16),
                    1,
                    List.of("copper")
            );
        }

        assertEquals(1, reader.sourceCalls.get());
    }

    private void publishRockSnapshot(
            RenderDataCacheStore cache,
            Path save
    ) {
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
    }

    private void publishResourceSnapshot(
            RenderDataCacheStore cache,
            Path save
    ) {
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        snapshot.resourceIndexStore().publishBlockCatalog(
                List.of(new BlockInfo(
                        9,
                        "game:ore-copper-native"
                ))
        );
        List<ChunkPosition> positions = new OreChunkPositionPlanner().plan(
                new WorldMetadata(32, 64, 32),
                16,
                16,
                1,
                ActualBlockYFilter.unbounded()
        );
        java.util.ArrayList<ResourceChunkIndexEntry> entries =
                new java.util.ArrayList<>();
        for (ChunkPosition position : positions) {
            if (position.y() == 0) {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of(new ResourceOccurrence(
                                position,
                                9,
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
        snapshot.resourceIndexStore().publish(entries);
    }

    private Path save(String name) throws Exception {
        Path save = root.resolve(name).resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});
        return save;
    }

    private static final class CountingReader extends VcdbsReader {
        private final AtomicInteger sourceCalls = new AtomicInteger();

        private CountingReader() {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    7,
                    new BlockInfo(7, "game:rock-granite"),
                    9,
                    new BlockInfo(9, "game:ore-copper-native")
            );
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer
        ) {
            sourceCalls.incrementAndGet();
            int size = ChunkCoordinate.SIZE_BLOCKS;
            for (ChunkPosition position : positions) {
                int[] blocks = new int[size * size * size];
                Arrays.fill(blocks, 7);
                if (position.y() == 0) {
                    int index = (5 * size + 16) * size + 16;
                    blocks[index] = 9;
                }
                consumer.accept(SelectiveChunkVisit.decoded(
                        position,
                        cartographer.model.ParsedChunkFixtures.create(
                                new ChunkCoordinate(
                                        position.x(),
                                        position.y(),
                                        position.z()
                                ),
                                position.y() * size,
                                size,
                                size,
                                size,
                                blocks
                        )
                ));
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    positions.size(),
                    positions.size(),
                    0,
                    positions.size(),
                    0,
                    positions.size()
            );
        }
    }

    private static final class MetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(
                Connection connection,
                ProgressReporter progress
        ) {
            return new WorldMetadata(32, 64, 32);
        }
    }

    private static final class TestConnectionFactory
            extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    }
            );
        }
    }
}
