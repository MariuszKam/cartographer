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
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        CountDownLatch cancellations = new CountDownLatch(3);
        coordinator.setOnCancelled(scope -> {
            cancelled.add(scope);
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
        awaitLatch(foreground.cancelled, "foreground cancellation");
        assertTrue(coordinator.isActive(WorkstationOperationScope.LOCAL));
        assertTrue(coordinator.isActive(WorkstationOperationScope.DISCOVERY));

        assertTrue(onFx(coordinator::cancelPreferred));
        awaitLatch(local.cancelled, "local cancellation");
        assertTrue(coordinator.isActive(WorkstationOperationScope.DISCOVERY));

        assertTrue(onFx(coordinator::cancelPreferred));
        awaitLatch(discovery.cancelled, "discovery cancellation");
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
        awaitLatch(foreground.cancelled, "foreground cancellation");
        awaitLatch(discovery.cancelled, "discovery cancellation");
        awaitLatch(local.cancelled, "local cancellation");
        flushFxEvents();

        assertFalse(coordinator.isActive(WorkstationOperationScope.FOREGROUND));
        assertFalse(coordinator.isActive(WorkstationOperationScope.DISCOVERY));
        assertFalse(coordinator.isActive(WorkstationOperationScope.LOCAL));
    }

    private static WorkstationOperationCoordinator coordinator() {
        return onFx(() -> new WorkstationOperationCoordinator(new RecordingProgressView()));
    }

    private static BlockingOperation blockingOperation() {
        return new BlockingOperation(new CountDownLatch(1), new CountDownLatch(1));
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

    private static final class BlockingOperation {
        private final CountDownLatch started;
        private final CountDownLatch cancelled;

        private BlockingOperation(
                CountDownLatch started,
                CountDownLatch cancelled
        ) {
            this.started = started;
            this.cancelled = cancelled;
        }

        private String run() {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("blocking operation unexpectedly resumed");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancelled.countDown();
                throw new CancellationException("cancelled by coordinator");
            }
        }

        private void awaitStarted(String description) {
            awaitLatch(started, description);
        }
    }

    private static final class RecordingProgressView implements WorkstationProgressView {
        @Override
        public void setStatus(String status) {
        }

        @Override
        public void setIndeterminateProgress() {
        }

        @Override
        public void setProgress(double current, double total) {
        }
    }
}
