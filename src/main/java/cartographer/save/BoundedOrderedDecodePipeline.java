package cartographer.save;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

final class BoundedOrderedDecodePipeline<T> implements AutoCloseable {
    private final ArrayDeque<Future<T>> pending = new ArrayDeque<>();
    private final int maxInFlight;
    private final Consumer<T> consumer;
    private final ThreadPoolExecutor executor;
    private boolean finished;

    BoundedOrderedDecodePipeline(
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
                java.util.concurrent.TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxInFlight - workerCount),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    void submit(Callable<T> task) {
        Objects.requireNonNull(task, "task is required");
        ensureOpen();

        if (pending.size() >= maxInFlight) {
            resolveOldest();
        }

        try {
            pending.addLast(executor.submit(task));
        } catch (RejectedExecutionException exception) {
            abort();
            throw new IllegalStateException(
                    "decode task could not be submitted",
                    exception
            );
        }
    }

    void finish() {
        if (finished) {
            return;
        }

        try {
            while (!pending.isEmpty()) {
                resolveOldest();
            }
            executor.shutdown();
            awaitTermination();
            finished = true;
        } catch (RuntimeException | Error failure) {
            abort();
            throw failure;
        }
    }

    @Override
    public void close() {
        if (finished) {
            return;
        }
        for (Future<T> future : pending) {
            future.cancel(true);
        }
        pending.clear();
        executor.shutdownNow();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        finished = true;
    }

    int pendingCount() {
        return pending.size();
    }

    private void resolveOldest() {
        Future<T> future = pending.removeFirst();
        T result;
        try {
            result = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            abort();
            throw new IllegalStateException(
                    "interrupted while resolving decode task",
                    interrupted
            );
        } catch (ExecutionException execution) {
            abort();
            throw new IllegalStateException(
                    "decode task failed",
                    execution.getCause()
            );
        } catch (java.util.concurrent.CancellationException cancelled) {
            abort();
            throw new IllegalStateException(
                    "decode task was cancelled",
                    cancelled
            );
        }

        try {
            consumer.accept(result);
        } catch (RuntimeException | Error failure) {
            abort();
            throw failure;
        }
    }

    private void ensureOpen() {
        if (finished || executor.isShutdown()) {
            throw new IllegalStateException("decode pipeline is closed");
        }
    }

    private void abort() {
        for (Future<T> future : pending) {
            future.cancel(true);
        }
        pending.clear();
        executor.shutdownNow();
        finished = true;
    }

    private void awaitTermination() {
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while shutting down decode pipeline",
                    interrupted
            );
        }
    }
}
