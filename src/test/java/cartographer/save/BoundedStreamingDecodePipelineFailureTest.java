package cartographer.save;

import cartographer.testing.ConcurrencyTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
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

@ConcurrencyTest
class BoundedStreamingDecodePipelineFailureTest extends BoundedStreamingDecodePipelineTestSupport {

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
            awaitLatch(started, "started");

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
            awaitLatch(firstStarted, "firstStarted");

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
        awaitLatch(firstStarted, "firstStarted");
        awaitLatch(interruptObserved, "interruptObserved");
        assertEquals(1, controllerFinished.getCount());
        assertEquals(1, firstStopped.getCount());

        releaseFirst.countDown();
        awaitLatch(controllerFinished, "controllerFinished");
        assertSame(cause, failure.get().getCause());
        pipeline.close();
        awaitLatch(firstStopped, "firstStopped");
        joinThread(controller, "controller");
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
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(interruptObserved, "interruptObserved");
            controller.interrupt();
            assertEquals(1, controllerFinished.getCount());
            assertEquals(1, firstStopped.getCount());

            releaseFirst.countDown();
            awaitLatch(controllerFinished, "controllerFinished");
            assertSame(cause, failure.get().getCause());
            assertTrue(controlInterrupted.get());
            awaitLatch(firstStopped, "firstStopped");
            awaitWorkerTermination(worker);
        } finally {
            releaseFirst.countDown();
            joinThread(controller, "controller");
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
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(consumerEntered, "consumerEntered");
            awaitLatch(interruptObserved, "interruptObserved");
            controller.interrupt();
            assertEquals(1, controllerFinished.getCount());

            releaseFirst.countDown();
            awaitLatch(controllerFinished, "controllerFinished");
            assertSame(cause, failure.get());
            assertTrue(controlInterrupted.get());
            awaitLatch(firstStopped, "firstStopped");
            awaitWorkerTermination(worker);
        } finally {
            releaseFirst.countDown();
            joinThread(controller, "controller");
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
            awaitLatch(started, "started");
            awaitLatch(interruptObserved, "interruptObserved");
            assertEquals(1, closeReturned.getCount());
            releaseWorker.countDown();
            awaitLatch(closeReturned, "closeReturned");
            assertEquals(null, failure.get());
            assertEquals(0, pipeline.inFlightCount());
            awaitWorkerTermination(worker);
        } finally {
            releaseWorker.countDown();
            joinThread(controller, "controller");
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
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(firstBlocked, "firstBlocked");
            awaitLatch(submitted, "submitted");
            awaitLatch(firstInterrupted, "firstInterrupted");
            awaitLatch(closeReturned, "closeReturned");
            assertEquals(null, failure.get());
            assertEquals(1, secondStarted.getCount());
            assertTrue(callbacks.isEmpty());
            assertEquals(0, pipeline.inFlightCount());
            awaitWorkerTermination(worker);
        } finally {
            joinThread(controller, "controller");
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
            awaitLatch(started, "started");
            awaitLatch(closeStarted, "closeStarted");
            awaitLatch(interruptObserved, "interruptObserved");
            controller.interrupt();
            assertEquals(1, closeReturned.getCount());
            releaseWorker.countDown();
            awaitLatch(closeReturned, "closeReturned");
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(failure.get().getCause() instanceof InterruptedException);
            assertTrue(interrupted.get());
            awaitWorkerTermination(worker);
        } finally {
            releaseWorker.countDown();
            joinThread(controller, "controller");
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
            awaitLatch(firstStarted, "firstStarted");
            awaitLatch(bothSubmitted, "bothSubmitted");
            allowFailure.countDown();
            awaitLatch(submitted, "submitted");
            awaitLatch(controllerFinished, "controllerFinished");
            assertSame(cause, failure.get().getCause());
            assertTrue(callbacks.isEmpty());
            awaitWorkerTermination(worker);
        } finally {
            allowFailure.countDown();
            joinThread(controller, "controller");
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
            awaitWorkerTermination(worker);
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

}
