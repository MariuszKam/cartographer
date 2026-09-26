package cartographer.save;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

abstract class BoundedStreamingDecodePipelineTestSupport {
    protected static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 10;

    protected static void awaitUninterruptibly(CountDownLatch latch) {
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

    protected static void awaitControllerLatch(CountDownLatch latch) {
        awaitLatch(latch, "controller synchronization");
    }

    protected static void awaitLatch(CountDownLatch latch, String description) {
        try {
            if (!latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError(description + " was not signalled");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " was interrupted", interruption);
        }
    }

    protected static void joinThread(Thread thread, String description) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(TEST_DEADLOCK_TIMEOUT_SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " join was interrupted", interruption);
        }
        assertFalse(thread.isAlive(), description + " did not terminate");
    }

    protected static void awaitWorkerTermination(AtomicReference<Thread> worker) {
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

}
