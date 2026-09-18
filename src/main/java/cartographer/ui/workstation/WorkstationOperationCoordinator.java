package cartographer.ui.workstation;

import cartographer.application.ProgressReporter;
import javafx.concurrent.Task;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * UI-owned launcher for Workstation background operations.
 *
 * <p>This class centralizes JavaFX Task creation, progress bridging and daemon-thread
 * startup only. Operation semantics, busy scopes, stale-result handling and cancellation
 * remain owned by the controller/callers.</p>
 */
public final class WorkstationOperationCoordinator {
    private final WorkstationView workstation;

    public WorkstationOperationCoordinator(WorkstationView workstation) {
        this.workstation = Objects.requireNonNull(workstation, "workstation is required");
    }

    public <T> Task<T> submit(
            String threadName,
            Supplier<T> operation,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed
    ) {
        Objects.requireNonNull(operation, "operation is required");
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return operation.get();
            }
        };
        configure(task, onSucceeded, onFailed, false);
        start(task, threadName);
        return task;
    }

    public <T> Task<T> submitProgress(
            String threadName,
            Function<ProgressReporter, T> operation,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed
    ) {
        Objects.requireNonNull(operation, "operation is required");
        ProgressTask<T> task = new ProgressTask<>() {
            @Override
            protected T call() {
                return operation.apply(progressReporter(this));
            }
        };
        configure(task, onSucceeded, onFailed, true);
        start(task, threadName);
        return task;
    }

    private <T> void configure(
            Task<T> task,
            Consumer<T> onSucceeded,
            Consumer<Throwable> onFailed,
            boolean reportProgress
    ) {
        Objects.requireNonNull(task, "task is required");
        Consumer<T> success = onSucceeded == null ? ignored -> { } : onSucceeded;
        Consumer<Throwable> failure = onFailed == null ? ignored -> { } : onFailed;
        if (reportProgress) {
            wireProgress(task);
        }
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
    }

    private void start(Task<?> task, String threadName) {
        String name = Objects.requireNonNull(threadName, "thread name is required").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("thread name must not be blank");
        }
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void wireProgress(Task<?> task) {
        task.messageProperty().addListener((observable, oldMessage, message) -> {
            if (message != null && !message.isBlank()) {
                workstation.setStatus(message);
            }
        });
        task.progressProperty().addListener((observable, oldProgress, progress) -> {
            if (progress == null || progress.doubleValue() < 0.0) {
                workstation.setIndeterminateProgress();
            } else {
                workstation.setProgress(progress.doubleValue(), 1.0);
            }
        });
    }

    private ProgressReporter progressReporter(ProgressTask<?> task) {
        return new ProgressReporter() {
            @Override
            public void start(String stage) {
                task.reportStage(stage);
            }

            @Override
            public void progress(String stage, int current, int total) {
                task.reportProgress(stage, current, total);
            }

            @Override
            public void done(String stage) {
                task.reportDone(stage);
            }
        };
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
