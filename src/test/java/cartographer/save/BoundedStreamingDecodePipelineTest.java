package cartographer.save;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStreamingDecodePipelineTest {
    @Test
    void laterCompletionIsConsumedBeforeSlowFirstTask() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, values::add)) {
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

            pipeline.submit(() -> 3);
            assertTrue(values.contains(2));
            assertFalse(values.contains(1));

            releaseFirst.countDown();
            pipeline.finish();
        }
        assertEquals(Set.of(1, 2, 3), new HashSet<>(values));
    }

    @Test
    void newSubmissionDoesNotWaitForSlowOldestTask() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        List<Integer> values = new ArrayList<>();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, values::add)) {
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
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(secondDone.await(1, TimeUnit.SECONDS));
            assertTrue(thirdStarted.await(1, TimeUnit.SECONDS));

            pipeline.submit(() -> 4);
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
        assertTrue(worker.get() != consumer.get());
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
        assertFalse(worker.get().isAlive());
    }

    @Test
    void twoWorkersCanRunConcurrently() throws Exception {
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
            assertTrue(active.await(1, TimeUnit.SECONDS));
            release.countDown();
            pipeline.finish();
        }
    }

    @Test
    void inFlightNeverExceedsConfiguredMaximum() throws Exception {
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
    void completedResultRetainsCapacityUntilConsumerReturns() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
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
                pipeline.submit(() -> 2);
                pipeline.submit(() -> {
                    releaseFirst.await();
                    return 3;
                });
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
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(consumerEntered.await(1, TimeUnit.SECONDS));
            assertEquals(3, pipeline.inFlightCount());

            releaseConsumer.countDown();
            assertTrue(submitReturned.await(1, TimeUnit.SECONDS));
            assertEquals(null, submitFailure.get());
            releaseFirst.countDown();
            control.join();
        } finally {
            releaseConsumer.countDown();
            releaseFirst.countDown();
            control.join(1000);
            pipeline.close();
        }
    }

    @Test
    void producerBackpressureUsesTotalOutstandingWork() throws Exception {
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
            assertTrue(ready.await(1, TimeUnit.SECONDS));
            assertTrue(submitAttempted.await(1, TimeUnit.SECONDS));
            assertEquals(3, pipeline.inFlightCount());
            releaseSecond.countDown();
            assertTrue(submitReturned.await(1, TimeUnit.SECONDS));
            releaseFirst.countDown();
            control.join();
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            control.join(1000);
            pipeline.close();
        }
    }

    @Test
    void nonOldestCompletionReleasesCapacity() throws Exception {
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
            assertTrue(ready.await(1, TimeUnit.SECONDS));
            assertTrue(submitAttempted.await(1, TimeUnit.SECONDS));
            assertEquals(3, pipeline.inFlightCount());
            releaseSecond.countDown();
            assertTrue(submitReturned.await(1, TimeUnit.SECONDS));
            assertTrue(values.contains(2));
            assertFalse(values.contains(1));
            releaseFirst.countDown();
            releaseThird.countDown();
            control.join();
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            releaseThird.countDown();
            control.join(1000);
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

    @Test
    void workerFailurePreservesCauseAndAbortsOutstandingWork() throws Exception {
        IllegalArgumentException cause = new IllegalArgumentException("known");
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> { })) {
            pipeline.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException interruption) {
                    interrupted.set(true);
                }
                return 1;
            });
            pipeline.submit(() -> {
                throw cause;
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    pipeline::finish
            );
            assertSame(cause, failure.getCause());
            assertTrue(interrupted.get());
            assertThrows(IllegalStateException.class, () -> pipeline.submit(() -> 3));
        }
    }

    @Test
    void fastLaterFailureIsObservedWithoutWaitingForSlowFirstTask() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        IllegalStateException cause = new IllegalStateException("later failure");

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> { })) {
            pipeline.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> {
                throw cause;
            });
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    pipeline::finish
            );
            assertSame(cause, failure.getCause());
            releaseFirst.countDown();
        }
    }

    @Test
    void abortWaitsForNonCooperativeWorkerBeforePropagatingFailure() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStopped = new CountDownLatch(1);
        CountDownLatch controllerFinished = new CountDownLatch(1);
        IllegalStateException cause = new IllegalStateException("later failure");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, value -> { });
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException interruption) {
                        interruptObserved.countDown();
                        // Deliberately remain blocked after cancellation until released.
                        awaitUninterruptibly(releaseFirst);
                    } finally {
                        firstStopped.countDown();
                    }
                    return 1;
                });
                awaitControllerLatch(firstStarted);
                pipeline.submit(() -> {
                    throw cause;
                });
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                controllerFinished.countDown();
            }
        });
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
        assertTrue(interruptObserved.await(1, TimeUnit.SECONDS));
        assertEquals(1, controllerFinished.getCount());
        assertEquals(1, firstStopped.getCount());

        releaseFirst.countDown();
        assertTrue(controllerFinished.await(1, TimeUnit.SECONDS));
        assertSame(cause, failure.get().getCause());
        pipeline.close();
        assertTrue(firstStopped.await(1, TimeUnit.SECONDS));
        controller.join();
    }

    @Test
    void interruptedWorkerCleanupPreservesPrimaryFailureAndQuiesces() throws Exception {
        IllegalStateException cause = new IllegalStateException("worker failure");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStopped = new CountDownLatch(1);
        CountDownLatch controllerFinished = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean controlInterrupted = new AtomicBoolean();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, value -> { });
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException interruption) {
                        interruptObserved.countDown();
                        awaitUninterruptibly(releaseFirst);
                    } finally {
                        firstStopped.countDown();
                    }
                    return 1;
                });
                awaitControllerLatch(firstStarted);
                pipeline.submit(() -> { throw cause; });
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
                controlInterrupted.set(Thread.currentThread().isInterrupted());
            } finally {
                controllerFinished.countDown();
            }
        });

        try {
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(interruptObserved.await(1, TimeUnit.SECONDS));
            controller.interrupt();
            assertEquals(1, controllerFinished.getCount());
            assertEquals(1, firstStopped.getCount());

            releaseFirst.countDown();
            assertTrue(controllerFinished.await(1, TimeUnit.SECONDS));
            assertSame(cause, failure.get().getCause());
            assertTrue(controlInterrupted.get());
            assertTrue(firstStopped.await(1, TimeUnit.SECONDS));
            assertFalse(worker.get().isAlive());
        } finally {
            releaseFirst.countDown();
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void interruptedConsumerCleanupPreservesPrimaryFailureAndQuiesces() throws Exception {
        RuntimeException cause = new RuntimeException("consumer failure");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstStopped = new CountDownLatch(1);
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch controllerFinished = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean controlInterrupted = new AtomicBoolean();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                    if (value == 2) {
                        consumerEntered.countDown();
                        throw cause;
                    }
                });
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException interruption) {
                        interruptObserved.countDown();
                        awaitUninterruptibly(releaseFirst);
                    } finally {
                        firstStopped.countDown();
                    }
                    return 1;
                });
                awaitControllerLatch(firstStarted);
                pipeline.submit(() -> 2);
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
                controlInterrupted.set(Thread.currentThread().isInterrupted());
            } finally {
                controllerFinished.countDown();
            }
        });

        try {
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(consumerEntered.await(1, TimeUnit.SECONDS));
            assertTrue(interruptObserved.await(1, TimeUnit.SECONDS));
            controller.interrupt();
            assertEquals(1, controllerFinished.getCount());

            releaseFirst.countDown();
            assertTrue(controllerFinished.await(1, TimeUnit.SECONDS));
            assertSame(cause, failure.get());
            assertTrue(controlInterrupted.get());
            assertTrue(firstStopped.await(1, TimeUnit.SECONDS));
            assertFalse(worker.get().isAlive());
        } finally {
            releaseFirst.countDown();
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void closeWaitsForNonCooperativeWorkerQuiescence() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, value -> { });
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException interruption) {
                        interruptObserved.countDown();
                        awaitUninterruptibly(releaseWorker);
                    }
                    return 1;
                });
                awaitControllerLatch(started);
                pipeline.close();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                closeReturned.countDown();
            }
        });

        try {
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(interruptObserved.await(1, TimeUnit.SECONDS));
            assertEquals(1, closeReturned.getCount());
            releaseWorker.countDown();
            assertTrue(closeReturned.await(1, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertEquals(0, pipeline.inFlightCount());
            assertFalse(worker.get().isAlive());
        } finally {
            releaseWorker.countDown();
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void closeCancelsRunningAndQueuedWorkWithoutCallbacks() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstBlocked = new CountDownLatch(1);
        CountDownLatch firstInterrupted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch submitted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        List<Integer> callbacks = new ArrayList<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, callbacks::add);
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    firstStarted.countDown();
                    try {
                        firstBlocked.countDown();
                        new CountDownLatch(1).await();
                    } catch (InterruptedException interruption) {
                        firstInterrupted.countDown();
                    }
                    return 1;
                });
                awaitControllerLatch(firstBlocked);
                pipeline.submit(() -> {
                    secondStarted.countDown();
                    return 2;
                });
                submitted.countDown();
                pipeline.close();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                try {
                    awaitWorkerTermination(worker);
                } finally {
                    closeReturned.countDown();
                }
            }
        });

        try {
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(firstBlocked.await(1, TimeUnit.SECONDS));
            assertTrue(submitted.await(1, TimeUnit.SECONDS));
            assertTrue(firstInterrupted.await(1, TimeUnit.SECONDS));
            assertTrue(closeReturned.await(1, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertEquals(1, secondStarted.getCount());
            assertTrue(callbacks.isEmpty());
            assertEquals(0, pipeline.inFlightCount());
            assertFalse(worker.get().isAlive());
        } finally {
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void interruptedCloseRestoresInterruptAfterWorkerQuiescence() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
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
                        new CountDownLatch(1).await();
                    } catch (InterruptedException interruption) {
                        interruptObserved.countDown();
                        awaitUninterruptibly(releaseWorker);
                    }
                    return 1;
                });
                awaitControllerLatch(started);
                closeStarted.countDown();
                pipeline.close();
            } catch (Throwable thrown) {
                failure.set(thrown);
                interrupted.set(Thread.currentThread().isInterrupted());
            } finally {
                try {
                    awaitWorkerTermination(worker);
                } finally {
                    closeReturned.countDown();
                }
            }
        });

        try {
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS));
            assertTrue(interruptObserved.await(1, TimeUnit.SECONDS));
            awaitThreadState(controller, Thread.State.WAITING, Thread.State.TIMED_WAITING);
            controller.interrupt();
            assertEquals(1, closeReturned.getCount());
            releaseWorker.countDown();
            assertTrue(closeReturned.await(1, TimeUnit.SECONDS));
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(failure.get().getCause() instanceof InterruptedException);
            assertTrue(interrupted.get());
            assertFalse(worker.get().isAlive());
        } finally {
            releaseWorker.countDown();
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void fatalWorkerFailureStopsCallbacksAfterFailureBoundary() throws Exception {
        IllegalArgumentException cause = new IllegalArgumentException("decode exploded");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch allowFailure = new CountDownLatch(1);
        CountDownLatch bothSubmitted = new CountDownLatch(1);
        CountDownLatch controllerFinished = new CountDownLatch(1);
        CountDownLatch submitted = new CountDownLatch(1);
        List<Integer> callbacks = new ArrayList<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, callbacks::add);
        Thread controller = Thread.ofPlatform().start(() -> {
            try {
                pipeline.submit(() -> {
                    worker.set(Thread.currentThread());
                    firstStarted.countDown();
                    awaitUninterruptibly(allowFailure);
                    throw cause;
                });
                awaitControllerLatch(firstStarted);
                pipeline.submit(() -> 2);
                bothSubmitted.countDown();
                submitted.countDown();
                pipeline.finish();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                controllerFinished.countDown();
            }
        });

        try {
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(bothSubmitted.await(1, TimeUnit.SECONDS));
            allowFailure.countDown();
            assertTrue(submitted.await(1, TimeUnit.SECONDS));
            assertTrue(controllerFinished.await(1, TimeUnit.SECONDS));
            assertSame(cause, failure.get().getCause());
            assertTrue(callbacks.isEmpty());
            assertFalse(worker.get().isAlive());
        } finally {
            allowFailure.countDown();
            controller.join(1000);
            pipeline.close();
        }
    }

    @Test
    void closeIsIdempotentAfterFatalAbort() {
        IllegalStateException cause = new IllegalStateException("fatal decode");
        AtomicReference<Thread> worker = new AtomicReference<>();
        List<Integer> callbacks = new ArrayList<>();
        BoundedStreamingDecodePipeline<Integer> pipeline =
                new BoundedStreamingDecodePipeline<>(1, 2, callbacks::add);

        try {
            pipeline.submit(() -> {
                worker.set(Thread.currentThread());
                throw cause;
            });
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    pipeline::finish
            );
            assertSame(cause, failure.getCause());
            pipeline.close();
            pipeline.close();
            assertThrows(IllegalStateException.class, () -> pipeline.submit(() -> 1));
            assertTrue(callbacks.isEmpty());
            assertFalse(worker.get().isAlive());
            assertEquals(0, pipeline.inFlightCount());
        } finally {
            pipeline.close();
        }
    }

    @Test
    void tryWithResourcesPreservesWorkerFailure() {
        IllegalArgumentException cause = new IllegalArgumentException("primary worker failure");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> {
                    try (BoundedStreamingDecodePipeline<Integer> pipeline =
                                 new BoundedStreamingDecodePipeline<>(1, 2, value -> { })) {
                        pipeline.submit(() -> { throw cause; });
                        pipeline.finish();
                    }
                }
        );

        assertSame(cause, failure.getCause());
    }

    @Test
    void tryWithResourcesPreservesConsumerFailure() {
        RuntimeException cause = new RuntimeException("primary consumer failure");

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> {
                    try (BoundedStreamingDecodePipeline<Integer> pipeline =
                                 new BoundedStreamingDecodePipeline<>(1, 2, value -> {
                                     throw cause;
                                 })) {
                        pipeline.submit(() -> 1);
                        pipeline.finish();
                    }
                }
        );

        assertSame(cause, failure);
    }

    @Test
    void consumerFailureRemainsPrimaryAndStopsCallbacks() throws Exception {
        RuntimeException cause = new RuntimeException("consumer failure");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        List<Integer> values = new ArrayList<>();

        try (BoundedStreamingDecodePipeline<Integer> pipeline =
                     new BoundedStreamingDecodePipeline<>(2, 3, value -> {
                         callbacks.incrementAndGet();
                         values.add(value);
                         throw cause;
                     })) {
            pipeline.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await();
                return 1;
            });
            pipeline.submit(() -> 2);
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            RuntimeException failure = assertThrows(RuntimeException.class, pipeline::finish);
            assertSame(cause, failure);
            assertEquals(1, callbacks.get());
            releaseFirst.countDown();
        }
    }

    @Test
    void closeCancelsAndInterruptsOutstandingWork() throws Exception {
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
        assertTrue(started.await(1, TimeUnit.SECONDS));
        pipeline.close();
        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertEquals(0, pipeline.inFlightCount());
    }

    @Test
    void interruptedCompletionWaitRestoresInterruptStatusAndAborts() throws Exception {
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
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(blocked.await(1, TimeUnit.SECONDS));
            assertTrue(finishStarted.await(1, TimeUnit.SECONDS));
            awaitThreadState(controller, Thread.State.WAITING, Thread.State.TIMED_WAITING);
            controller.interrupt();
            assertTrue(controllerFinished.await(1, TimeUnit.SECONDS));
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(failure.get().getCause() instanceof InterruptedException);
            assertTrue(interrupted.get());
            assertTrue(workerInterrupted.await(1, TimeUnit.SECONDS));
            assertFalse(worker.get().isAlive());
        } finally {
            pipeline.close();
            controller.join(1000);
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

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitControllerLatch(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("controller synchronization latch was not signalled");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError("controller synchronization was interrupted", interruption);
        }
    }

    private static void awaitWorkerTermination(AtomicReference<Thread> worker) {
        Thread workerThread = worker.get();
        if (workerThread == null) {
            throw new AssertionError("worker thread was not captured");
        }

        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (workerThread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new AssertionError("worker did not terminate");
            }
            try {
                workerThread.join(Math.max(1L, Math.min(
                        TimeUnit.NANOSECONDS.toMillis(remaining), 100L)));
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitThreadState(Thread thread, Thread.State... expectedStates) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!contains(expectedStates, thread.getState())) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                        "controller did not reach a wait state; state was " + thread.getState()
                );
            }
            Thread.yield();
        }
    }

    private static boolean contains(Thread.State[] states, Thread.State actual) {
        for (Thread.State state : states) {
            if (state == actual) {
                return true;
            }
        }
        return false;
    }
}
