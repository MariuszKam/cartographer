package cartographer.application;

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
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.UpperRockTile;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldSnapshotHeader;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderRockMapSnapshotRoutingTest {
    @TempDir
    Path root;

    @Test
    void completeUpperRockSnapshotSkipsSourceSelectiveTraversal()
            throws Exception {
        Path save = save("hit");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-hit"));
        publishRockTile(cache, save);

        CountingReader reader = new CountingReader();
        MetadataReader metadataReader = new MetadataReader();
        TestConnectionFactory connectionFactory =
                new TestConnectionFactory();
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader,
                metadataReader,
                new RockMapRenderer(),
                new SaveSessionFactory(
                        connectionFactory,
                        reader,
                        metadataReader
                ),
                Optional.of(cache)
        );

        RenderRockMapResult result = useCase.execute(request(save));

        assertEquals(0, connectionFactory.opens.get());
        assertEquals(0, reader.sourceCalls.get());
        assertEquals(0, result.chunkStats().uniquePositionsRequested());
        assertEquals(1, result.map().observedCount());
        assertEquals(4, result.map().noRockCount());
    }

    @Test
    void missingSnapshotTileFallsBackToAuthoritativeSource()
            throws Exception {
        Path save = save("miss");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-miss"));
        WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();

        CountingReader reader = new CountingReader();
        MetadataReader metadataReader = new MetadataReader();
        TestConnectionFactory connectionFactory =
                new TestConnectionFactory();
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader,
                metadataReader,
                new RockMapRenderer(),
                new SaveSessionFactory(
                        connectionFactory,
                        reader,
                        metadataReader
                ),
                Optional.of(cache)
        );

        RenderRockMapResult result = useCase.execute(request(save));

        assertEquals(1, connectionFactory.opens.get());
        assertEquals(1, reader.sourceCalls.get());
        assertEquals(5, result.map().observedCount());
    }

    @Test
    void atYModeRemainsSourceAuthoritativeEvenWhenUpperRockSnapshotExists()
            throws Exception {
        Path save = save("aty");
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-aty"));
        publishRockTile(cache, save);

        CountingReader reader = new CountingReader();
        MetadataReader metadataReader = new MetadataReader();
        TestConnectionFactory connectionFactory =
                new TestConnectionFactory();
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader,
                metadataReader,
                new RockMapRenderer(),
                new SaveSessionFactory(
                        connectionFactory,
                        reader,
                        metadataReader
                ),
                Optional.of(cache)
        );

        useCase.execute(new RenderRockMapRequest(
                save,
                cartographer.geology.rock.RockMapMode.AT_Y,
                1,
                Optional.of(new WorldPosition(16, 0, 16)),
                java.util.OptionalInt.of(31),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty()
        ));

        assertEquals(1, connectionFactory.opens.get());
        assertEquals(1, reader.sourceCalls.get());
    }

    private RenderRockMapRequest request(Path save) {
        return new RenderRockMapRequest(
                save,
                cartographer.geology.rock.RockMapMode.UPPER_ROCK,
                1,
                Optional.of(new WorldPosition(16, 0, 16)),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty()
        );
    }

    private void publishRockTile(
            RenderDataCacheStore cache,
            Path save
    ) {
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        snapshot.headerStore().publish(new WorldSnapshotHeader(
                new WorldMetadata(32, 64, 32),
                Map.of(7, new BlockInfo(7, "game:rock-granite")),
                Optional.empty()
        ));
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
        snapshot.upperRockTileStore().publish(java.util.List.of(
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
                    new BlockInfo(7, "game:rock-granite")
            );
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            sourceCalls.incrementAndGet();
            int size = ChunkCoordinate.SIZE_BLOCKS;
            for (ChunkPosition position : positions) {
                int[] blocks = new int[size * size * size];
                Arrays.fill(blocks, 7);
                consumer.accept(SelectiveChunkVisit.decoded(
                        position,
                        new ParsedChunk(
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
        private final AtomicInteger opens = new AtomicInteger();

        @Override
        public Connection openReadOnly(Path savePath) {
            opens.incrementAndGet();
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
