package cartographer.ui.workstation;

import cartographer.progress.ProgressReporter;
import javafx.concurrent.Task;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * UI-owned coordinator for bounded Workstation operations.
 *
 * <p>One active operation is allowed per scope. Starting a replacement
 * operation cancels/interupts the previous operation in that scope. Completion
 * callbacks are generation-gated so stale results cannot update the UI.</p>
 */
public final class WorkstationOperationCoordinator {
    private final WorkstationProgressView workstation;
    private final Map<WorkstationOperationScope, ActiveOperation<?>> active =
            new EnumMap<>(WorkstationOperationScope.class);
    private long generation;
    private Consumer<WorkstationOperationScope> cancelledListener = ignored -> { };

    public WorkstationOperationCoordinator(WorkstationProgressView workstation) {
        this.workstation = Objects.requireNonNull(workstation, "workstation is required");
    }

    public void setOnCancelled(
            Consumer<WorkstationOperationScope> listener
    ) {
        cancelledListener = listener == null ? ignored -> { } : listener;
    }

    public <T> Task<T> submit(
            WorkstationOperationScope scope,
            String type,
            Supplier<T> operation,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed
    ) {
        Objects.requireNonNull(operation, "operation is required");
        long token = nextGeneration();
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                requireCurrent(scope, token, this);
                return operation.get();
            }
        };
        ActiveOperation<T> activeOperation = new ActiveOperation<>(
                token,
                scope,
                normalized(type, "type"),
                task
        );
        register(activeOperation);
        configure(activeOperation, onSucceeded, onFailed, false);
        start(activeOperation, activeOperation.type());
        return task;
    }

    public <T> Task<T> submitProgress(
            WorkstationOperationScope scope,
            String type,
            Function<ProgressReporter, T> operation,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed
    ) {
        Objects.requireNonNull(operation, "operation is required");
        long token = nextGeneration();
        ProgressTask<T> task = new ProgressTask<>() {
            @Override
            protected T call() {
                requireCurrent(scope, token, this);
                return operation.apply(progressReporter(this, scope, token));
            }
        };
        ActiveOperation<T> activeOperation = new ActiveOperation<>(
                token,
                scope,
                normalized(type, "type"),
                task
        );
        register(activeOperation);
        configure(activeOperation, onSucceeded, onFailed, true);
        start(activeOperation, activeOperation.type());
        return task;
    }

    public boolean cancel(WorkstationOperationScope scope) {
        Objects.requireNonNull(scope, "scope is required");
        ActiveOperation<?> operation;
        synchronized (this) {
            operation = active.get(scope);
            if (operation == null || operation.terminal()) {
                return false;
            }
            operation.state = WorkstationOperationState.CANCEL_REQUESTED;
        }
        interrupt(operation);
        return true;
    }

    public boolean cancelPreferred() {
        if (cancel(WorkstationOperationScope.FOREGROUND)) {
            return true;
        }
        if (cancel(WorkstationOperationScope.LOCAL)) {
            return true;
        }
        return cancel(WorkstationOperationScope.DISCOVERY);
    }

    public void cancelAll() {
        for (WorkstationOperationScope scope : WorkstationOperationScope.values()) {
            cancel(scope);
        }
    }

    public synchronized boolean isActive(WorkstationOperationScope scope) {
        ActiveOperation<?> operation = active.get(
                Objects.requireNonNull(scope, "scope is required")
        );
        return operation != null && !operation.terminal();
    }

    private synchronized long nextGeneration() {
        return ++generation;
    }

    private <T> void register(ActiveOperation<T> operation) {
        ActiveOperation<?> previous;
        synchronized (this) {
            previous = active.put(operation.scope(), operation);
        }
        if (previous != null && !previous.terminal()) {
            previous.state = WorkstationOperationState.CANCEL_REQUESTED;
            interrupt(previous);
        }
    }

    private <T> void configure(
            ActiveOperation<T> operation,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed,
            boolean reportProgress
    ) {
        Task<T> task = operation.task();
        Consumer<T> success = onSucceeded == null ? ignored -> { } : onSucceeded;
        Consumer<Throwable> failure = onFailed == null ? ignored -> { } : onFailed;
        if (reportProgress) {
            wireProgress(operation);
        }
        task.setOnSucceeded(event -> {
            if (operation.cancelRequested()) {
                if (complete(operation, WorkstationOperationState.CANCELLED)) {
                    cancelledListener.accept(operation.scope());
                }
                return;
            }
            if (!complete(operation, WorkstationOperationState.SUCCEEDED)) {
                return;
            }
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            Throwable problem = task.getException();
            if (operation.cancelRequested()) {
                if (complete(operation, WorkstationOperationState.CANCELLED)) {
                    cancelledListener.accept(operation.scope());
                }
                return;
            }
            if (!complete(operation, WorkstationOperationState.FAILED)) {
                return;
            }
            failure.accept(problem);
        });
        task.setOnCancelled(event -> {
            if (!complete(operation, WorkstationOperationState.CANCELLED)) {
                return;
            }
            cancelledListener.accept(operation.scope());
        });
    }

    private void start(
            ActiveOperation<?> operation,
            String threadName
    ) {
        Thread worker = new Thread(operation.task(), threadName);
        worker.setDaemon(true);
        operation.worker = worker;
        worker.start();
    }

    private boolean complete(
            ActiveOperation<?> operation,
            WorkstationOperationState state
    ) {
        synchronized (this) {
            if (active.get(operation.scope()) != operation) {
                return false;
            }
            operation.state = state;
            active.remove(operation.scope());
            return true;
        }
    }

    private void wireProgress(ActiveOperation<?> operation) {
        Task<?> task = operation.task();
        task.messageProperty().addListener((observable, oldMessage, message) -> {
            if (message != null && !message.isBlank()
                    && shouldPublish(operation)) {
                workstation.setStatus(message);
            }
        });
        task.progressProperty().addListener((observable, oldProgress, progress) -> {
            if (!shouldPublish(operation)) {
                return;
            }
            if (progress == null || progress.doubleValue() < 0.0) {
                workstation.setIndeterminateProgress();
            } else {
                workstation.setProgress(progress.doubleValue(), 1.0);
            }
        });
    }

    private synchronized boolean shouldPublish(ActiveOperation<?> operation) {
        if (active.get(operation.scope()) != operation) {
            return false;
        }
        ActiveOperation<?> foreground =
                active.get(WorkstationOperationScope.FOREGROUND);
        if (foreground != null) {
            return foreground == operation;
        }
        ActiveOperation<?> local =
                active.get(WorkstationOperationScope.LOCAL);
        if (local != null) {
            return local == operation;
        }
        return active.get(WorkstationOperationScope.DISCOVERY) == operation;
    }

    private ProgressReporter progressReporter(
            ProgressTask<?> task,
            WorkstationOperationScope scope,
            long token
    ) {
        return new ProgressReporter() {
            @Override
            public void start(String stage) {
                requireCurrent(scope, token, task);
                task.reportStage(stage);
            }

            @Override
            public void progress(String stage, int current, int total) {
                requireCurrent(scope, token, task);
                task.reportProgress(stage, current, total);
            }

            @Override
            public void done(String stage) {
                requireCurrent(scope, token, task);
                task.reportDone(stage);
            }
        };
    }

    private void requireCurrent(
            WorkstationOperationScope scope,
            long token,
            Task<?> task
    ) {
        boolean current;
        synchronized (this) {
            ActiveOperation<?> operation = active.get(scope);
            current = operation != null
                    && operation.generation() == token
                    && operation.task() == task;
        }
        ActiveOperation<?> operation;
        synchronized (this) {
            operation = active.get(scope);
        }
        if (!current
                || (operation != null && operation.cancelRequested())
                || task.isCancelled()
                || Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "workstation operation cancelled or superseded"
            );
        }
    }

    private void interrupt(ActiveOperation<?> operation) {
        Thread worker = operation.worker;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private String normalized(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static final class ActiveOperation<T> {
        private final long generation;
        private final WorkstationOperationScope scope;
        private final String type;
        private final Task<T> task;
        private volatile Thread worker;
        private volatile WorkstationOperationState state =
                WorkstationOperationState.RUNNING;

        private ActiveOperation(
                long generation,
                WorkstationOperationScope scope,
                String type,
                Task<T> task
        ) {
            this.generation = generation;
            this.scope = Objects.requireNonNull(scope, "scope is required");
            this.type = Objects.requireNonNull(type, "type is required");
            this.task = Objects.requireNonNull(task, "task is required");
        }

        long generation() { return generation; }
        WorkstationOperationScope scope() { return scope; }
        String type() { return type; }
        Task<T> task() { return task; }

        boolean cancelRequested() {
            return state == WorkstationOperationState.CANCEL_REQUESTED;
        }

        boolean terminal() {
            return state == WorkstationOperationState.SUCCEEDED
                    || state == WorkstationOperationState.FAILED
                    || state == WorkstationOperationState.CANCELLED;
        }

    }

    private abstract static class ProgressTask<T> extends Task<T> {
        final void reportStage(String stage) {
            updateMessage(stage);
            updateProgress(-1, 1);
        }

        final void reportProgress(String stage, int current, int total) {
            updateMessage(stage);
            if (total <= 0) {
                updateProgress(-1, 1);
            } else {
                updateProgress(current, total);
            }
        }

        final void reportDone(String stage) {
            updateMessage(stage);
            updateProgress(1, 1);
        }
    }
}
