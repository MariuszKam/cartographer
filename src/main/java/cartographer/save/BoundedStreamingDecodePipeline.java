package cartographer.save;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Completion-driven bounded decode work owned by one control thread.
 *
 * <p>The completion queue is intentionally the standard unbounded queue used
 * by {@link ExecutorCompletionService}. The outstanding-future count is the
 * authoritative bound, so the queue cannot contain more completed outcomes
 * than {@code maxInFlight}.</p>
 */
final class BoundedStreamingDecodePipeline<T> implements AutoCloseable {
    private static final long ABORT_TERMINATION_WAIT_NANOS =
            TimeUnit.MILLISECONDS.toNanos(100);

    private enum State {
        OPEN,
        FINISHED,
        ABORTED
    }

    private final int maxInFlight;
    private final Consumer<T> consumer;
    private final ThreadPoolExecutor executor;
    private final ExecutorCompletionService<T> completionService;
    private final Set<Future<T>> outstanding = new HashSet<>();
    private int inFlight;
    private State state = State.OPEN;

    BoundedStreamingDecodePipeline(
            int workerCount,
            int maxInFlight,
            Consumer<T> consumer
    ) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (maxInFlight <= workerCount) {
            throw new IllegalArgumentException(
                    "maxInFlight must be greater than workerCount"
            );
        }
        this.consumer = Objects.requireNonNull(consumer, "consumer is required");
        this.maxInFlight = maxInFlight;

        ThreadFactory threadFactory = Thread.ofPlatform()
                .name("cartographer-decode-", 0)
                .factory();
        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                // Logical in-flight accounting, rather than this queue, is authoritative.
                new java.util.concurrent.LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.completionService = new ExecutorCompletionService<>(executor);
    }

    /**
     * Submits one task, draining available completions and applying
     * backpressure against the total outstanding-work bound.
     */
    void submit(Callable<T> task) {
        Objects.requireNonNull(task, "task is required");
        ensureOpen();

        drainCompletedWithoutBlocking();
        while (inFlight >= maxInFlight) {
            consume(takeCompletion());
        }

        try {
            Future<T> future = completionService.submit(task);
            outstanding.add(future);
            inFlight++;
        } catch (RejectedExecutionException rejection) {
            abort();
            throw new IllegalStateException(
                    "decode task could not be submitted",
                    rejection
            );
        }
    }

    /**
     * Drains every submitted task by completion order and shuts down workers.
     */
    void finish() {
        if (state == State.FINISHED) {
            return;
        }
        ensureOpen();

        try {
            while (inFlight > 0) {
                consume(takeCompletion());
            }
            executor.shutdown();
            InterruptedException interruption = awaitTermination();
            if (interruption != null) {
                state = State.ABORTED;
                executor.shutdownNow();
                throw new IllegalStateException(
                        "interrupted while shutting down decode pipeline",
                        interruption
                );
            }
            state = State.FINISHED;
        } catch (RuntimeException | Error failure) {
            if (state == State.OPEN) {
                abort();
            }
            throw failure;
        }
    }

    /**
     * Cancels outstanding work and performs bounded best-effort worker
     * cleanup. Cleanup never replaces an already-propagating failure.
     */
    @Override
    public void close() {
        if (state == State.FINISHED) {
            return;
        }
        abort();
    }

    /** Package-private observation for invariant tests. */
    int inFlightCount() {
        return inFlight;
    }

    private void drainCompletedWithoutBlocking() {
        Future<T> completed;
        while ((completed = completionService.poll()) != null) {
            consume(completed);
        }
    }

    private Future<T> takeCompletion() {
        try {
            return completionService.take();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            abort();
            throw new IllegalStateException(
                    "interrupted while waiting for decode completion",
                    interruption
            );
        }
    }

    private void consume(Future<T> future) {
        T result;
        try {
            result = future.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            abort();
            throw new IllegalStateException(
                    "interrupted while resolving decode completion",
                    interruption
            );
        } catch (ExecutionException failure) {
            abort();
            throw new IllegalStateException(
                    "decode task failed",
                    failure.getCause()
            );
        } catch (CancellationException cancellation) {
            abort();
            throw new IllegalStateException(
                    "decode task was cancelled",
                    cancellation
            );
        }

        try {
            consumer.accept(result);
        } catch (RuntimeException | Error failure) {
            abort();
            throw failure;
        }

        if (!outstanding.remove(future)) {
            abort();
            throw new IllegalStateException(
                    "decode pipeline lost completion bookkeeping"
            );
        }
        inFlight--;
    }

    private void ensureOpen() {
        if (state != State.OPEN) {
            throw new IllegalStateException("decode pipeline is closed");
        }
    }

    private void abort() {
        if (state == State.FINISHED) {
            return;
        }
        state = State.ABORTED;
        for (Future<T> future : outstanding) {
            future.cancel(true);
        }
        outstanding.clear();
        inFlight = 0;
        executor.shutdownNow();
        awaitTerminationBestEffort();
    }

    /**
     * Fatal cleanup must not hide the original failure behind an uncooperative
     * task. The short bound is a lifecycle safety limit, not a performance
     * tuning parameter; close may be called again after the task is released.
     */
    private void awaitTerminationBestEffort() {
        long deadline = System.nanoTime() + ABORT_TERMINATION_WAIT_NANOS;
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    break;
                }
            } catch (InterruptedException interruption) {
                interrupted = true;
                break;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Awaits termination even if interrupted, then restores the interrupt
     * status. Returning the first interruption lets finish report it without
     * allowing cleanup to mask the primary failure.
     */
    private InterruptedException awaitTermination() {
        InterruptedException firstInterruption = null;
        boolean terminated = false;
        while (!terminated) {
            try {
                terminated = executor.awaitTermination(
                        Long.MAX_VALUE,
                        TimeUnit.NANOSECONDS
                );
            } catch (InterruptedException interruption) {
                if (firstInterruption == null) {
                    firstInterruption = interruption;
                }
            }
        }
        if (firstInterruption != null) {
            Thread.currentThread().interrupt();
        }
        return firstInterruption;
    }
}
