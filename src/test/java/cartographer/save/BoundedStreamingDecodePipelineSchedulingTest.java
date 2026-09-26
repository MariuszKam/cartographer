package cartographer.save;

import cartographer.testing.ConcurrencyTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ConcurrencyTest
class BoundedStreamingDecodePipelineSchedulingTest extends BoundedStreamingDecodePipelineTestSupport {

    @Test
    void laterCompletionIsConsumedBeforeSlowFirstTask() {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        CountDownLatch secondConsumed = new CountDownLatch(1);
        List<Integer> values =
                Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread controller = Thread.ofPlatform().start(() -> {
            try (BoundedStreamingDecodePipeline<Integer> pipeline =
                         new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                             values.add(value);
                             if (value == 2) {
                                 secondConsumed.countDown();
                             }
                         })) {
                pipeline.submit(() -> {
                    firstStarted.countDown();
                    releaseFirst.await();
                    return 1;
                });
                pipeline.submit(() -> {
                    secondDone.countDown();
                    return 2;
                });
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        try {
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(secondDone, "secondDone");
            awaitLatch(secondConsumed, "secondConsumed");

            assertTrue(values.contains(2));
            assertFalse(values.contains(1));

            releaseFirst.countDown();
            joinThread(controller, "controller");
            assertNull(failure.get());
        } finally {
            releaseFirst.countDown();
            joinThread(controller, "controller");
        }

        assertEquals(Set.of(1, 2), new HashSet<>(values));
    }

    @Test
    void newSubmissionDoesNotWaitForSlowOldestTask() {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch secondConsumed = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                         values.add(value);
                         if (value == 2) {
                             secondConsumed.countDown();
                         }
                     })) {
            pipeline.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                secondDone.countDown();
                return 2;
            });
            pipeline.submit(() -> {
                thirdStarted.countDown();
                releaseFirst.await();
                return 3;
            });
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(secondDone, "secondDone");
            awaitLatch(thirdStarted, "thirdStarted");

            pipeline.submit(() -> 4);
            awaitLatch(secondConsumed, "secondConsumed");
            assertTrue(values.contains(2));
            assertFalse(values.contains(1));

            releaseFirst.countDown();
            pipeline.finish();
        }
    }

    @Test
    void consumerRunsOnControlThreadAndNotWorkerThread() {
        Thread control = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Thread> consumer = new AtomicReference<>();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(1, 2, value ->
                             consumer.set(Thread.currentThread()))) {
            pipeline.submit(() -> {
                worker.set(Thread.currentThread());
                return 1;
            });
            pipeline.finish();
        }

        assertSame(control, consumer.get());
        assertNotSame(worker.get(), consumer.get());
    }

    @Test
    void workersAreNamedPlatformThreads() {
        AtomicReference<Thread> worker = new AtomicReference<>();

        try (BoundedStreamingDecodePipeline<Boolean> pipeline =
                     new BoundedStreamingDecodePipeline<>(1, 2, value -> { })) {
            pipeline.submit(() -> {
                worker.set(Thread.currentThread());
                return Thread.currentThread().isVirtual();
            });
            pipeline.finish();
        }

        assertFalse(worker.get().isVirtual());
        assertTrue(worker.get().getName().startsWith("cartographer-decode-"));
        awaitWorkerTermination(worker);
    }

    @Test
    void twoWorkersCanRunConcurrently() {
        CountDownLatch active = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> { })) {
            pipeline.submit(() -> {
                active.countDown();
                release.await();
                return 1;
            });
            pipeline.submit(() -> {
                active.countDown();
                release.await();
                return 2;
            });
            awaitLatch(active, "active");
            release.countDown();
            pipeline.finish();
        }
    }

    @Test
    void inFlightNeverExceedsConfiguredMaximum() {
        CountDownLatch release = new CountDownLatch(1);
        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> { })) {
            pipeline.submit(() -> {
                release.await();
                return 1;
            });
            pipeline.submit(() -> {
                release.await();
                return 2;
            });
            pipeline.submit(() -> {
                release.await();
                return 3;
            });
            assertTrue(pipeline.inFlightCount() <= 3);
            release.countDown();
            pipeline.finish();
            assertEquals(0, pipeline.inFlightCount());
        }
    }

    @Test
    void completedResultRetainsCapacityUntilConsumerReturns() {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch allowSecondComplete = new CountDownLatch(1);
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch thirdSubmitted = new CountDownLatch(1);
        CountDownLatch submitReturned = new CountDownLatch(1);
        AtomicReference<Throwable> submitFailure = new AtomicReference<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                         if (value == 2) {
                             consumerEntered.countDown();
                             awaitUninterruptibly(releaseConsumer);
                         }
                     });
        Thread control = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    firstStarted.countDown();
                    releaseFirst.await();
                    return 1;
                });
                pipeline.submit(() -> {
                    secondReady.countDown();
                    awaitUninterruptibly(allowSecondComplete);
                    return 2;
                });
                pipeline.submit(() -> {
                    releaseFirst.await();
                    return 3;
                });
                thirdSubmitted.countDown();
                try {
                    pipeline.submit(() -> 4);
                } catch (Throwable failure) {
                    submitFailure.set(failure);
                } finally {
                    submitReturned.countDown();
                }
                pipeline.finish();
            } catch (Throwable failure) {
                submitFailure.compareAndSet(null, failure);
                submitReturned.countDown();
            }
        });
        try {
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(secondReady, "secondReady");
            awaitLatch(thirdSubmitted, "thirdSubmitted");
            allowSecondComplete.countDown();
            awaitLatch(consumerEntered, "consumerEntered");
            assertEquals(3, pipeline.inFlightCount());

            releaseConsumer.countDown();
            allowSecondComplete.countDown();
            awaitLatch(submitReturned, "submitReturned");
            assertNull(submitFailure.get());
            releaseFirst.countDown();
            joinThread(control, "control");
        } finally {
            releaseConsumer.countDown();
            allowSecondComplete.countDown();
            releaseFirst.countDown();
            joinThread(control, "control");
            pipeline.close();
        }
    }

    @Test
    void producerBackpressureUsesTotalOutstandingWork() {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch submitReturned = new CountDownLatch(1);

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, value -> { });
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch submitAttempted = new CountDownLatch(1);
        Thread control = Thread.ofPlatform().start(() -> {
            pipeline.submit(() -> {
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                releaseSecond.await();
                return 2;
            });
            pipeline.submit(() -> {
                releaseFirst.await();
                return 3;
            });
            ready.countDown();
            submitAttempted.countDown();
            pipeline.submit(() -> 4);
            submitReturned.countDown();
            pipeline.finish();
        });
        try {
            awaitLatch(ready, "ready");
            awaitLatch(submitAttempted, "submitAttempted");
            assertEquals(3, pipeline.inFlightCount());
            releaseSecond.countDown();
            awaitLatch(submitReturned, "submitReturned");
            releaseFirst.countDown();
            joinThread(control, "control");
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            joinThread(control, "control");
            pipeline.close();
        }
    }

    @Test
    void nonOldestCompletionReleasesCapacity() {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch releaseThird = new CountDownLatch(1);
        CountDownLatch submitReturned = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, values::add);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch submitAttempted = new CountDownLatch(1);
        Thread control = Thread.ofPlatform().start(() -> {
            pipeline.submit(() -> {
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                releaseSecond.await();
                return 2;
            });
            pipeline.submit(() -> {
                releaseThird.await();
                return 3;
            });
            ready.countDown();
            submitAttempted.countDown();
            pipeline.submit(() -> 4);
            submitReturned.countDown();
            pipeline.finish();
        });
        try {
            awaitLatch(ready, "ready");
            awaitLatch(submitAttempted, "submitAttempted");
            assertEquals(3, pipeline.inFlightCount());
            releaseSecond.countDown();
            awaitLatch(submitReturned, "submitReturned");
            assertTrue(values.contains(2));
            assertFalse(values.contains(1));
            releaseFirst.countDown();
            releaseThird.countDown();
            joinThread(control, "control");
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            releaseThird.countDown();
            joinThread(control, "control");
            pipeline.close();
        }
    }

    @Test
    void successfulFinishDeliversEveryResultExactlyOnceWithoutOrderingRequirement() {
        int count = 100;
        List<Integer> values = new ArrayList<>();
        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(4, 8, values::add)) {
            for (int id = 0; id < count; id++) {
                int value = id;
                pipeline.submit(() -> value);
            }
            pipeline.finish();
        }

        assertEquals(count, values.size());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
                60, 61, 62, 63, 64, 65, 66, 67, 68, 69,
                70, 71, 72, 73, 74, 75, 76, 77, 78, 79,
                80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
                90, 91, 92, 93, 94, 95, 96, 97, 98, 99),
                new HashSet<>(values));
    }

}
