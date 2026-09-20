package cartographer.application;

import cartographer.testing.ConcurrencyTest;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SaveSessionLifecycleProbe;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverObservedSurfaceResourcesUseCaseTest {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 5;

    @Test
    void suppliesAllCandidateIdsToOneSelectiveScan() {
        FakeReader reader = new FakeReader();
        DiscoverObservedSurfaceResourcesUseCase useCase = useCase(reader);

        var result = useCase.execute(new DiscoverObservedSurfaceResourcesRequest(
                Path.of("world.vcdbs"),
                1,
                java.util.Optional.of(new WorldPosition(16, 0, 16))
        ));

        assertEquals(1, reader.selectiveScanCalls);
        assertArrayEquals(new int[] {1, 2}, reader.lastWantedIds);
        assertEquals(List.of("game:nativecopper"),
                result.observedResources().observedQualifiedResourceKeys());
    }

    @Test
    void zeroSelectiveCallbacksMakePlannedTargetsUnavailable() {
        FakeReader reader = new FakeReader();
        reader.skipSelectiveCallbacks = true;
        DiscoverObservedSurfaceResourcesUseCase useCase = useCase(reader);

        var result = useCase.execute(new DiscoverObservedSurfaceResourcesRequest(
                Path.of("world.vcdbs"), 1,
                java.util.Optional.of(new WorldPosition(16, 0, 16))
        ));

        assertEquals(1, reader.selectiveScanCalls);
        assertTrue(result.scan().unavailablePositions() > 0);
        assertEquals(0, result.scan().observedTargets());
    }

    @Test
    @ConcurrencyTest
    void interruptionClosesOperationScopedSession() throws Exception {
        CountDownLatch selectiveStarted = new CountDownLatch(1);
        CountDownLatch selectiveInterrupted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SaveSessionLifecycleProbe probe = SaveSessionLifecycleProbe.recording();
        BlockingReader reader = new BlockingReader(
                selectiveStarted,
                selectiveInterrupted
        );
        WorldMetadataReader metadata = metadataReader();
        DiscoverObservedSurfaceResourcesUseCase useCase =
                new DiscoverObservedSurfaceResourcesUseCase(
                        reader,
                        new SaveSessionFactory(
                                new TestConnectionFactory(),
                                reader,
                                metadata,
                                probe
                        )
                );

        Thread operation = Thread.ofPlatform().start(() -> {
            try {
                useCase.execute(
                        new DiscoverObservedSurfaceResourcesRequest(
                                Path.of("cancel-world.vcdbs"),
                                1,
                                java.util.Optional.of(
                                        new WorldPosition(16, 0, 16)
                                )
                        ),
                        ProgressReporter.NONE
                );
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        try {
            awaitLatch(selectiveStarted, "selective scan started");
            operation.interrupt();
            awaitLatch(selectiveInterrupted, "selective scan interrupted");
            joinThread(operation, "surface discovery operation");

            assertTrue(failure.get() instanceof CancellationException);
            assertEquals(
                    new SaveSessionLifecycleProbe.Snapshot(1, 1),
                    probe.snapshot()
            );
        } finally {
            operation.interrupt();
            joinThread(operation, "surface discovery operation");
        }
    }

    private static void awaitLatch(
            CountDownLatch latch,
            String description
    ) throws InterruptedException {
        assertTrue(
                latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                description + " was not signalled"
        );
    }

    private static void joinThread(
            Thread thread,
            String description
    ) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        assertFalse(thread.isAlive(), description + " did not terminate");
    }

    private DiscoverObservedSurfaceResourcesUseCase useCase(
            FakeReader reader
    ) {
        WorldMetadataReader metadata = metadataReader();
        return new DiscoverObservedSurfaceResourcesUseCase(
                reader,
                new SaveSessionFactory(
                        new TestConnectionFactory(),
                        reader,
                        metadata
                )
        );
    }

    private WorldMetadataReader metadataReader() {
        return new WorldMetadataReader(null, null) {
            @Override
            protected WorldMetadata read(
                    Connection connection,
                    ProgressReporter progress
            ) {
                return new WorldMetadata(32, 64, 32);
            }
        };
    }

    private static final class FakeReader extends VcdbsReader {
        private int selectiveScanCalls;
        private int[] lastWantedIds = new int[0];
        private boolean skipSelectiveCallbacks;

        private FakeReader() {
            super(new PlayerDataParser(), new MapChunkParser(),
                    new ChunkParser(), new RegistryParser());
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    1, new BlockInfo(1,
                            "game:looseores-nativecopper-granite-free"),
                    2, new BlockInfo(2,
                            "game:loosestones-obsidian-free")
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            consumer.accept(new MapChunk(
                    new MapChunkCoordinate(0, 0),
                    new int[MapChunk.HEIGHT_VALUE_COUNT],
                    filledHeights(5)
            ));
            return new MapChunkStreamStats(
                    coordinates.size(), 1, 1, 1, 0, 0
            );
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            selectiveScanCalls++;
            lastWantedIds = Arrays.copyOf(wantedBlockIds, wantedBlockIds.length);
            if (skipSelectiveCallbacks) {
                return new SelectiveChunkStreamStats(
                        positions.size(), 0, 0, 0, 0, 0, 0, 0);
            }
            ParsedChunk chunk = chunkWithBlock(16, 6, 1);
            consumer.accept(SelectiveChunkVisit.decoded(
                    new ChunkPosition(0, 0, 0, 0), chunk
            ));
            return new SelectiveChunkStreamStats(
                    positions.size(), 1, 1, 1, 0, 1, 0, 0
            );
        }

        private int[] filledHeights(int value) {
            int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(heights, value);
            return heights;
        }

        private ParsedChunk chunkWithBlock(int worldX, int worldY, int blockId) {
            int[] blocks = new int[32 * 32 * 32];
            blocks[(worldY * 32 + 16) * 32 + worldX] = blockId;
            return new ParsedChunk(
                    new ChunkCoordinate(0, 0, 0), 0, 32, 32, 32, blocks
            );
        }
    }
    private static final class TestConnectionFactory extends SqliteSaveConnection {
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

    private static final class BlockingReader extends VcdbsReader {
        private final CountDownLatch started;
        private final CountDownLatch interrupted;

        private BlockingReader(
                CountDownLatch started,
                CountDownLatch interrupted
        ) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
            this.started = started;
            this.interrupted = interrupted;
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    1,
                    new BlockInfo(
                            1,
                            "game:looseores-nativecopper-granite-free"
                    )
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            consumer.accept(new MapChunk(
                    new MapChunkCoordinate(0, 0),
                    new int[MapChunk.HEIGHT_VALUE_COUNT],
                    filledHeightsStatic(5)
            ));
            return new MapChunkStreamStats(
                    coordinates.size(),
                    1,
                    1,
                    1,
                    0,
                    0
            );
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interruption) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled");
            }
            throw new AssertionError("blocking scan unexpectedly resumed");
        }

        private static int[] filledHeightsStatic(int value) {
            int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(heights, value);
            return heights;
        }
    }

}
