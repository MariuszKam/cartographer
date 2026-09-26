package cartographer.ui.workstation;

import cartographer.testing.ConcurrencyTest;
import cartographer.testing.IntegrationTest;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@ConcurrencyTest
class WorkstationOperationCoordinatorTest {
    private static final long TEST_DEADLOCK_TIMEOUT_SECONDS = 10;

    @BeforeAll
    static void startJavaFxRuntime() {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        awaitLatch(started, "JavaFX startup");
    }

    @AfterAll
    static void stopJavaFxRuntime() {
        Platform.exit();
    }

    @Test
    void supersedingOperationSuppressesStaleSuccessCallback() throws Exception {
        WorkstationOperationCoordinator coordinator = coordinator();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondSucceeded = new CountDownLatch(1);
        AtomicBoolean staleSucceeded = new AtomicBoolean();

        Task<String> first = onFx(() -> coordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "first",
                () -> waitIgnoringInterrupt(firstStarted, releaseFirst, "first"),
                ignored -> staleSucceeded.set(true),
                ignored -> { }
        ));
        awaitLatch(firstStarted, "first operation start");

        Task<String> second = onFx(() -> coordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "second",
                () -> "second",
                ignored -> secondSucceeded.countDown(),
                ignored -> { }
        ));

        releaseFirst.countDown();
        assertEquals("second", second.get(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        first.get(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        awaitLatch(secondSucceeded, "second success callback");
        flushFxEvents();

        assertFalse(staleSucceeded.get());
        assertFalse(coordinator.isActive(WorkstationOperationScope.FOREGROUND));
    }

    @Test
    void cancelPreferredHonorsForegroundThenLocalThenDiscovery() {
        WorkstationOperationCoordinator coordinator = coordinator();
        List<WorkstationOperationScope> cancelled =
                Collections.synchronizedList(new ArrayList<>());
        Map<WorkstationOperationScope, CountDownLatch> cancellationEvents =
                cancellationEvents();
        CountDownLatch cancellations = new CountDownLatch(3);
        coordinator.setOnCancelled(scope -> {
            cancelled.add(scope);
            cancellationEvents.get(scope).countDown();
            cancellations.countDown();
        });

        BlockingOperation discovery = blockingOperation();
        BlockingOperation local = blockingOperation();
        BlockingOperation foreground = blockingOperation();

        onFx(() -> coordinator.submit(
                WorkstationOperationScope.DISCOVERY,
                "discovery",
                discovery::run,
                ignored -> { },
                ignored -> { }
        ));
        onFx(() -> coordinator.submit(
                WorkstationOperationScope.LOCAL,
                "local",
                local::run,
                ignored -> { },
                ignored -> { }
        ));
        onFx(() -> coordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "foreground",
                foreground::run,
                ignored -> { },
                ignored -> { }
        ));

        discovery.awaitStarted("discovery start");
        local.awaitStarted("local start");
        foreground.awaitStarted("foreground start");

        assertTrue(onFx(coordinator::cancelPreferred));
        awaitLatch(
                cancellationEvents.get(WorkstationOperationScope.FOREGROUND),
                "foreground coordinator cancellation"
        );
        assertTrue(coordinator.isActive(WorkstationOperationScope.LOCAL));
        assertTrue(coordinator.isActive(WorkstationOperationScope.DISCOVERY));

        assertTrue(onFx(coordinator::cancelPreferred));
        awaitLatch(
                cancellationEvents.get(WorkstationOperationScope.LOCAL),
                "local coordinator cancellation"
        );
        assertTrue(coordinator.isActive(WorkstationOperationScope.DISCOVERY));

        assertTrue(onFx(coordinator::cancelPreferred));
        awaitLatch(
                cancellationEvents.get(WorkstationOperationScope.DISCOVERY),
                "discovery coordinator cancellation"
        );
        awaitLatch(cancellations, "cancel callbacks");
        flushFxEvents();

        assertEquals(
                List.of(
                        WorkstationOperationScope.FOREGROUND,
                        WorkstationOperationScope.LOCAL,
                        WorkstationOperationScope.DISCOVERY
                ),
                cancelled
        );
        assertFalse(onFx(coordinator::cancelPreferred));
    }

    @Test
    void cancelAllLeavesEveryScopeInactive() {
        WorkstationOperationCoordinator coordinator = coordinator();
        CountDownLatch cancellations = new CountDownLatch(3);
        coordinator.setOnCancelled(ignored -> cancellations.countDown());
        BlockingOperation foreground = blockingOperation();
        BlockingOperation discovery = blockingOperation();
        BlockingOperation local = blockingOperation();

        onFx(() -> coordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "foreground",
                foreground::run,
                ignored -> { },
                ignored -> { }
        ));
        onFx(() -> coordinator.submit(
                WorkstationOperationScope.DISCOVERY,
                "discovery",
                discovery::run,
                ignored -> { },
                ignored -> { }
        ));
        onFx(() -> coordinator.submit(
                WorkstationOperationScope.LOCAL,
                "local",
                local::run,
                ignored -> { },
                ignored -> { }
        ));

        foreground.awaitStarted("foreground start");
        discovery.awaitStarted("discovery start");
        local.awaitStarted("local start");

        onFx(() -> {
            coordinator.cancelAll();
            return null;
        });
        awaitLatch(cancellations, "cancel-all coordinator callbacks");
        flushFxEvents();

        assertFalse(coordinator.isActive(WorkstationOperationScope.FOREGROUND));
        assertFalse(coordinator.isActive(WorkstationOperationScope.DISCOVERY));
        assertFalse(coordinator.isActive(WorkstationOperationScope.LOCAL));
    }

    @Test
    void foregroundProgressSuppressesLocalAndDiscoveryProgress() {
        RecordingProgressView view = new RecordingProgressView();
        WorkstationOperationCoordinator coordinator = coordinator(view);
        ControlledProgressOperation discovery = new ControlledProgressOperation();
        ControlledProgressOperation local = new ControlledProgressOperation();
        ControlledProgressOperation foreground = new ControlledProgressOperation();

        try {
            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.DISCOVERY,
                    "discovery",
                    discovery::run,
                    ignored -> { },
                    ignored -> { }
            ));
            discovery.awaitStarted("discovery progress operation start");
            discovery.report("discovery-visible", 1, 10);
            flushFxEvents();
            assertEquals("discovery-visible", view.lastStatus());
            assertEquals(0.1, view.lastProgress());

            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.LOCAL,
                    "local",
                    local::run,
                    ignored -> { },
                    ignored -> { }
            ));
            local.awaitStarted("local progress operation start");
            local.report("local-visible", 2, 10);
            flushFxEvents();
            assertEquals("local-visible", view.lastStatus());
            assertEquals(0.2, view.lastProgress());

            discovery.report("discovery-suppressed", 9, 10);
            flushFxEvents();
            assertEquals("local-visible", view.lastStatus());
            assertEquals(0.2, view.lastProgress());

            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.FOREGROUND,
                    "foreground",
                    foreground::run,
                    ignored -> { },
                    ignored -> { }
            ));
            foreground.awaitStarted("foreground progress operation start");
            foreground.report("foreground-visible", 3, 10);
            flushFxEvents();
            assertEquals("foreground-visible", view.lastStatus());
            assertEquals(0.3, view.lastProgress());

            local.report("local-suppressed", 8, 10);
            discovery.report("discovery-still-suppressed", 8, 10);
            flushFxEvents();
            assertEquals("foreground-visible", view.lastStatus());
            assertEquals(0.3, view.lastProgress());
        } finally {
            foreground.complete();
            local.complete();
            discovery.complete();
            coordinator.cancelAll();
            flushFxEvents();
        }
    }

    @Test
    void lowerPriorityProgressResumesAfterHigherPriorityOperationCompletes() {
        RecordingProgressView view = new RecordingProgressView();
        WorkstationOperationCoordinator coordinator = coordinator(view);
        ControlledProgressOperation discovery = new ControlledProgressOperation();
        ControlledProgressOperation local = new ControlledProgressOperation();
        ControlledProgressOperation foreground = new ControlledProgressOperation();
        CountDownLatch foregroundSucceeded = new CountDownLatch(1);
        CountDownLatch localSucceeded = new CountDownLatch(1);

        try {
            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.DISCOVERY,
                    "discovery",
                    discovery::run,
                    ignored -> { },
                    ignored -> { }
            ));
            discovery.awaitStarted("discovery progress operation start");
            discovery.report("discovery-initial", 1, 10);

            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.LOCAL,
                    "local",
                    local::run,
                    ignored -> localSucceeded.countDown(),
                    ignored -> { }
            ));
            local.awaitStarted("local progress operation start");
            local.report("local-initial", 2, 10);

            onFx(() -> coordinator.submitProgress(
                    WorkstationOperationScope.FOREGROUND,
                    "foreground",
                    foreground::run,
                    ignored -> foregroundSucceeded.countDown(),
                    ignored -> { }
            ));
            foreground.awaitStarted("foreground progress operation start");
            foreground.report("foreground", 3, 10);
            flushFxEvents();
            assertEquals("foreground", view.lastStatus());

            foreground.complete();
            awaitLatch(foregroundSucceeded, "foreground success callback");
            local.report("local-resumed", 4, 10);
            flushFxEvents();
            assertEquals("local-resumed", view.lastStatus());
            assertEquals(0.4, view.lastProgress());

            local.complete();
            awaitLatch(localSucceeded, "local success callback");
            discovery.report("discovery-resumed", 5, 10);
            flushFxEvents();
            assertEquals("discovery-resumed", view.lastStatus());
            assertEquals(0.5, view.lastProgress());
        } finally {
            foreground.complete();
            local.complete();
            discovery.complete();
            coordinator.cancelAll();
            flushFxEvents();
        }
    }

    @Test
    void supersededFailureCannotInvokeStaleCallback() {
        WorkstationOperationCoordinator coordinator = coordinator();
        CountDownLatch staleStarted = new CountDownLatch(1);
        CountDownLatch releaseStale = new CountDownLatch(1);
        AtomicBoolean staleFailed = new AtomicBoolean();

        onFx(() -> coordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "stale",
                reporter -> {
                    staleStarted.countDown();
                    waitIgnoringInterrupt(staleStarted, releaseStale, "ignored");
                    throw new IllegalStateException("stale failure");
                },
                ignored -> { },
                ignored -> staleFailed.set(true)
        ));
        awaitLatch(staleStarted, "stale progress operation start");

        CountDownLatch currentSucceeded = new CountDownLatch(1);
        onFx(() -> coordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "current",
                reporter -> "current",
                ignored -> currentSucceeded.countDown(),
                ignored -> { }
        ));

        releaseStale.countDown();
        awaitLatch(currentSucceeded, "current progress success callback");
        flushFxEvents();

        assertFalse(staleFailed.get());
    }

    private static WorkstationOperationCoordinator coordinator() {
        return coordinator(new RecordingProgressView());
    }

    private static WorkstationOperationCoordinator coordinator(RecordingProgressView view) {
        return onFx(() -> new WorkstationOperationCoordinator(view));
    }

    private static BlockingOperation blockingOperation() {
        return new BlockingOperation(new CountDownLatch(1));
    }

    private static Map<WorkstationOperationScope, CountDownLatch> cancellationEvents() {
        Map<WorkstationOperationScope, CountDownLatch> events =
                new EnumMap<>(WorkstationOperationScope.class);
        for (WorkstationOperationScope scope : WorkstationOperationScope.values()) {
            events.put(scope, new CountDownLatch(1));
        }
        return events;
    }

    private static String waitIgnoringInterrupt(
            CountDownLatch started,
            CountDownLatch release,
            String result
    ) {
        started.countDown();
        boolean interrupted = false;
        while (true) {
            try {
                release.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private static void flushFxEvents() {
        onFx(() -> null);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        try {
            return future.get(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("JavaFX action did not complete", exception);
        }
    }

    private static void awaitLatch(CountDownLatch latch, String description) {
        try {
            if (!latch.await(TEST_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError(description + " was not signalled");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " was interrupted", exception);
        }
    }

    private record BlockingOperation(CountDownLatch started) {

        private String run() {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("blocking operation unexpectedly resumed");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled by coordinator");
            }
        }

        private void awaitStarted(String description) {
            awaitLatch(started, description);
        }
    }

    private static final class ControlledProgressOperation {
        private final CountDownLatch started = new CountDownLatch(1);
        private final LinkedBlockingQueue<ProgressCommand> commands =
                new LinkedBlockingQueue<>();

        private String run(cartographer.progress.ProgressReporter reporter) {
            started.countDown();
            while (true) {
                ProgressCommand command;
                try {
                    command = commands.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException(
                            "controlled progress operation cancelled"
                    );
                }
                if (command.complete()) {
                    return "done";
                }
                reporter.progress(
                        command.stage(),
                        command.current(),
                        command.total()
                );
                command.reported().countDown();
            }
        }

        private void awaitStarted(String description) {
            awaitLatch(started, description);
        }

        private void report(String stage, int current, int total) {
            CountDownLatch reported = new CountDownLatch(1);
            commands.add(new ProgressCommand(
                    stage,
                    current,
                    total,
                    false,
                    reported
            ));
            awaitLatch(reported, stage + " progress report");
        }

        private void complete() {
            commands.offer(new ProgressCommand(
                    "",
                    0,
                    0,
                    true,
                    new CountDownLatch(0)
            ));
        }
    }

    private record ProgressCommand(
            String stage,
            int current,
            int total,
            boolean complete,
            CountDownLatch reported
    ) {
    }

    private static final class RecordingProgressView implements WorkstationProgressView {
        private final AtomicReference<String> lastStatus = new AtomicReference<>();
        private final AtomicReference<Double> lastProgress = new AtomicReference<>();

        @Override
        public void setStatus(String status) {
            lastStatus.set(status);
        }

        @Override
        public void setIndeterminateProgress() {
            lastProgress.set(-1.0);
        }

        @Override
        public void setProgress(double current, double total) {
            lastProgress.set(total <= 0.0 ? -1.0 : current / total);
        }

        private String lastStatus() {
            return lastStatus.get();
        }

        private double lastProgress() {
            Double value = lastProgress.get();
            return value == null ? Double.NaN : value;
        }
    }
}
