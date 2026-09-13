package cartographer.save;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedOrderedDecodePipelineTest {
    @Test
    void deliversResultsInSubmissionOrderWhenWorkersCompleteOutOfOrder() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(2, 3, values::add)) {
            pipeline.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                secondDone.countDown();
                return 2;
            });
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(secondDone.await(1, TimeUnit.SECONDS));
            releaseFirst.countDown();
            pipeline.finish();
        }
        assertEquals(List.of(1, 2), values);
    }

    @Test
    void consumerRunsOnSubmittingThread() {
        Thread submitting = Thread.currentThread();
        AtomicReference<Thread> consuming = new AtomicReference<>();
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(1, 2, value -> consuming.set(Thread.currentThread()))) {
            pipeline.submit(() -> 1);
            pipeline.finish();
        }
        assertSame(submitting, consuming.get());
        assertFalse(consuming.get().isVirtual());
    }

    @Test
    void workersArePlatformThreads() {
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (BoundedOrderedDecodePipeline<Boolean> pipeline =
                     new BoundedOrderedDecodePipeline<>(1, 2, ignored -> { })) {
            pipeline.submit(() -> {
                worker.set(Thread.currentThread());
                return Thread.currentThread().isVirtual();
            });
            pipeline.finish();
        }
        assertFalse(worker.get().isVirtual());
        assertTrue(worker.get().getName().startsWith("cartographer-decode-"));
    }

    @Test
    void runsMultipleTasksConcurrently() throws Exception {
        CountDownLatch reached = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(2, 3, ignored -> { })) {
            pipeline.submit(() -> {
                reached.countDown();
                release.await();
                return 1;
            });
            pipeline.submit(() -> {
                reached.countDown();
                release.await();
                return 2;
            });
            assertTrue(reached.await(1, TimeUnit.SECONDS));
            release.countDown();
            pipeline.finish();
        }
    }

    @Test
    void blocksProducerWhenMaxInFlightIsReached() throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseOthers = new CountDownLatch(1);
        CountDownLatch submitReturned = new CountDownLatch(1);
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(2, 3, ignored -> { })) {
            pipeline.submit(() -> {
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                releaseOthers.await();
                return 2;
            });
            pipeline.submit(() -> {
                releaseOthers.await();
                return 3;
            });

            Thread submitter = Thread.ofPlatform().start(() -> {
                pipeline.submit(() -> 4);
                submitReturned.countDown();
            });
            assertFalse(submitReturned.await(100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(submitReturned.await(1, TimeUnit.SECONDS));
            releaseOthers.countDown();
            pipeline.finish();
            submitter.join();
        }
    }

    @Test
    void laterCompletedResultDoesNotBypassEarlierPendingResult() throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(2, 3, values::add)) {
            pipeline.submit(() -> {
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> 2);
            assertTrue(values.isEmpty());
            releaseFirst.countDown();
            pipeline.finish();
        }
        assertEquals(List.of(1, 2), values);
    }

    @Test
    void workerFailureStopsPipelineAndPreservesCause() {
        IllegalArgumentException cause = new IllegalArgumentException("known");
        List<Integer> values = new ArrayList<>();
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(1, 2, values::add)) {
            pipeline.submit(() -> {
                throw cause;
            });
            pipeline.submit(() -> 2);
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    pipeline::finish
            );
            assertSame(cause, failure.getCause());
            assertTrue(values.isEmpty());
        }
    }

    @Test
    void consumerFailureStopsPipeline() {
        RuntimeException cause = new RuntimeException("known");
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(1, 2, ignored -> { throw cause; })) {
            pipeline.submit(() -> 1);
            pipeline.submit(() -> 2);
            assertSame(cause, assertThrows(RuntimeException.class, pipeline::finish));
        }
    }

    @Test
    void closeCancelsOutstandingWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        BoundedOrderedDecodePipeline<Integer> pipeline =
                new BoundedOrderedDecodePipeline<>(1, 2, ignored -> { });
        pipeline.submit(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
            return 1;
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        pipeline.close();
        assertTrue(interrupted.get());
    }

    @Test
    void rejectsInvalidConfigurationAndNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOrderedDecodePipeline<>(0, 2, ignored -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOrderedDecodePipeline<>(2, 2, ignored -> { }));
        assertThrows(NullPointerException.class,
                () -> new BoundedOrderedDecodePipeline<Integer>(1, 2, null));
        try (BoundedOrderedDecodePipeline<Integer> pipeline =
                     new BoundedOrderedDecodePipeline<>(1, 2, ignored -> { })) {
            assertThrows(NullPointerException.class, () -> pipeline.submit(null));
        }
    }

    @Test
    void finishIsIdempotent() {
        BoundedOrderedDecodePipeline<Integer> pipeline =
                new BoundedOrderedDecodePipeline<>(1, 2, ignored -> { });
        pipeline.finish();
        pipeline.finish();
        pipeline.close();
    }
}
