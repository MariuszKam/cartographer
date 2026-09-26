package cartographer.save;

import cartographer.testing.ConcurrencyTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ConcurrencyTest
class BoundedStreamingDecodePipelineLifecycleTest extends BoundedStreamingDecodePipelineTestSupport {

    @Test
    void consumerFailureRemainsPrimaryAndStopsCallbacks() {
        RuntimeException cause = new RuntimeException("consumer failure");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                         callbacks.incrementAndGet();
                         throw cause;
                     })) {
            pipeline.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> 2);
            awaitLatch(firstStarted, "firstStarted");
            RuntimeException failure = assertThrows(RuntimeException.class, pipeline::finish);
            assertSame(cause, failure);
            assertEquals(1, callbacks.get());
            releaseFirst.countDown();
        }
    }

    @Test
    void closeCancelsAndInterruptsOutstandingWork() {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        pipeline.submit(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interruption) {
                interrupted.countDown();
            }
            return 1;
        });
        awaitLatch(started, "started");
        pipeline.close();
        awaitLatch(interrupted, "interrupted");
        assertEquals(0, pipeline.inFlightCount());
    }

    @Test
    void interruptedCompletionWaitRestoresInterruptStatusAndAborts() {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch finishStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        CountDownLatch controllerFinished = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    started.countDown();
                    try {
                        blocked.countDown();
                        new CountDownLatch(1).await();
                    } catch (InterruptedException interruption) {
                        workerInterrupted.countDown();
                    }
                    return 1;
                });
                finishStarted.countDown();
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
                interrupted.set(Thread.currentThread().isInterrupted());
            } finally {
                try {
                    awaitWorkerTermination(worker);
                } finally {
                    controllerFinished.countDown();
                }
            }
        });
        try {
            awaitLatch(started, "started");
            awaitLatch(blocked, "blocked");
            awaitLatch(finishStarted, "finishStarted");
            controller.interrupt();
            awaitLatch(controllerFinished, "controllerFinished");
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(failure.get().getCause() instanceof InterruptedException);
            assertTrue(interrupted.get());
            awaitLatch(workerInterrupted, "workerInterrupted");
            awaitWorkerTermination(worker);
        } finally {
            pipeline.close();
            joinThread(controller, "controller");
        }
    }

    @Test
    void finishIsIdempotentAndCloseAfterFinishIsSafe() {
        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        pipeline.finish();
        pipeline.finish();
        pipeline.close();
    }

    @Test
    void submitAfterFinishFails() {
        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        pipeline.finish();
        assertThrows(IllegalStateException.class, () -> pipeline.submit(() -> 1));
        pipeline.close();
    }

    @Test
    void submitAfterCloseFails() {
        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        pipeline.close();
        assertThrows(IllegalStateException.class, () -> pipeline.submit(() -> 1));
    }

    @Test
    void rejectsInvalidConfigurationAndNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedStreamingDecodePipeline<>(0, 2, value -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedStreamingDecodePipeline<>(2, 2, value -> { }));
        assertThrows(NullPointerException.class,
                () -> new BoundedStreamingDecodePipeline<Integer>(1, 2, null));
        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(1, 2, value -> { })) {
            assertThrows(NullPointerException.class, () -> pipeline.submit(null));
        }
    }

}
