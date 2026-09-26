package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.testing.ConcurrencyTest;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
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

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ConcurrencyTest
class RenderRockMapCancellationTest {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 5;

    @Test
    void interruptionClosesSessionBeforeOperationReturns() throws Exception {
        CountDownLatch scanStarted = new CountDownLatch(1);
        CountDownLatch scanInterrupted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TestConnectionFactory connections = new TestConnectionFactory();
        BlockingRockReader reader = new BlockingRockReader(
                scanStarted,
                scanInterrupted
        );
        WorldMetadataReader metadata = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection) {
                return new WorldMetadata(64, 64, 64);
            }
        };
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader,
                new RockMapRenderer(),
                new SaveSessionFactory(
                        connections,
                        reader,
                        metadata
                )
        );
        RenderRockMapRequest request = new RenderRockMapRequest(
                Path.of("cancel-rock.vcdbs"),
                RockMapMode.UPPER_ROCK,
                32,
                Optional.of(new WorldPosition(32, 0, 32)),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty()
        );

        Thread operation = Thread.ofPlatform().start(() -> {
            try {
                useCase.execute(request, ProgressReporter.NONE);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        try {
            awaitLatch(scanStarted, "rock scan started");
            operation.interrupt();
            awaitLatch(scanInterrupted, "rock scan interrupted");
            joinRockRenderThread(operation);

            assertTrue(failure.get() instanceof CancellationException);
            assertEquals(1, connections.opened());
            assertEquals(1, connections.closed());
        } finally {
            operation.interrupt();
            joinRockRenderThread(operation);
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

    private static void joinRockRenderThread(
            Thread thread
    ) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        assertFalse(thread.isAlive(), "rock render operation did not terminate");
    }

    private static final class BlockingRockReader extends VcdbsReader {
        private final CountDownLatch started;
        private final CountDownLatch interrupted;

        private BlockingRockReader(
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
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interruption) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled");
            }
            throw new AssertionError("ROCK scan unexpectedly resumed");
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        private int opened;
        private int closed;

        @Override
        public Connection openReadOnly(Path savePath) {
            opened++;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            closed++;
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    }
            );
        }

        private int opened() {
            return opened;
        }

        private int closed() {
            return closed;
        }
    }
}
