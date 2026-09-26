package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.testing.ConcurrencyTest;
import cartographer.model.ChunkPosition;
import cartographer.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsChunkLookupConcurrencyTest extends VcdbsReaderDirectChunkLookupTestSupport {

    @Test
    @ConcurrencyTest
    void tableStreamDecodeStillRunsConcurrently() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(first, second);
        BlockingChunkParser parser = new BlockingChunkParser();
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(parser, 2, 4);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread caller = Thread.ofPlatform().start(() -> {
            callerThread.set(Thread.currentThread());
            try {
                tableStream(
                        reader,
                        database,
                        List.of(first, second),
                        new ReadDiagnostics(),
                        ignored -> consumerThread.set(Thread.currentThread()),
                        ProgressReporter.NONE
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        try {
            awaitLatch(parser.bothStarted, "both workers started");
            parser.release.countDown();
            joinThread(caller, "caller");
        } finally {
            parser.release.countDown();
            joinThread(caller, "caller");
        }

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
        assertTrue(parser.workerThreads.stream().noneMatch(Thread::isVirtual));
    }

    @Test
    @ConcurrencyTest
    void parallelDecodeOverlapsWhileConsumerRemainsCallerThread() throws Exception {
        ChunkPosition first = new ChunkPosition(1, 0, 2, 0);
        ChunkPosition second = new ChunkPosition(3, 0, 4, 0);
        Path database = databaseWithRows(first, second);
        BlockingChunkParser parser = new BlockingChunkParser();
        VcdbsReader reader = VcdbsReaderFixtures.withChunkParser(parser, 2, 4);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread caller = Thread.ofPlatform().start(() -> {
            callerThread.set(Thread.currentThread());
            try {
                direct(
                        reader,
                        database,
                        List.of(first, second),
                        new ReadDiagnostics(),
                        ignored -> consumerThread.set(Thread.currentThread()),
                        ProgressReporter.NONE
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        try {
            awaitLatch(parser.bothStarted, "both workers started");
            parser.release.countDown();
            joinThread(caller, "caller");
        } finally {
            parser.release.countDown();
            joinThread(caller, "caller");
        }

        assertNull(failure.get());
        assertEquals(callerThread.get(), consumerThread.get());
        assertEquals(2, parser.workerThreads.size());
        assertTrue(parser.workerThreads.stream().noneMatch(Thread::isVirtual));
    }
}
