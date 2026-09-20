package cartographer.ui.update;

import cartographer.update.UpdateCheckResult;
import cartographer.update.UpdateCheckService;
import cartographer.update.UpdateManifest;
import cartographer.update.UpdatePreferences;
import cartographer.update.UpdatePreferencesStore;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DesktopUpdateController {
    public static final Duration DEFAULT_AUTOMATIC_CHECK_INTERVAL =
            Duration.ofHours(24);

    private final UpdateCheckService updateCheckService;
    private final UpdatePreferencesStore preferencesStore;
    private final UpdateCheckView view;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiDispatcher;
    private final Consumer<URI> releaseOpener;
    private final Clock clock;
    private final Duration automaticCheckInterval;
    private final AtomicBoolean checkInProgress = new AtomicBoolean();
    private final AtomicReference<URI> availableRelease =
            new AtomicReference<>();

    public DesktopUpdateController(
            UpdateCheckService updateCheckService,
            UpdatePreferencesStore preferencesStore,
            UpdateCheckView view,
            Executor backgroundExecutor,
            Consumer<Runnable> uiDispatcher,
            Consumer<URI> releaseOpener,
            Clock clock,
            Duration automaticCheckInterval
    ) {
        this.updateCheckService = Objects.requireNonNull(
                updateCheckService,
                "updateCheckService is required"
        );
        this.preferencesStore = Objects.requireNonNull(
                preferencesStore,
                "preferencesStore is required"
        );
        this.view = Objects.requireNonNull(view, "view is required");
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "backgroundExecutor is required"
        );
        this.uiDispatcher = Objects.requireNonNull(
                uiDispatcher,
                "uiDispatcher is required"
        );
        this.releaseOpener = Objects.requireNonNull(
                releaseOpener,
                "releaseOpener is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.automaticCheckInterval = Objects.requireNonNull(
                automaticCheckInterval,
                "automaticCheckInterval is required"
        );

        view.showCurrentVersion(updateCheckService.currentVersion());
        view.setOnCheckForUpdates(this::checkNow);
        view.setOnOpenUpdateRelease(this::openAvailableRelease);
    }

    public void startAutomaticCheck() {
        submit(false);
    }

    public void checkNow() {
        submit(true);
    }

    private void submit(boolean manual) {
        if (!checkInProgress.compareAndSet(false, true)) {
            return;
        }

        if (manual) {
            uiDispatcher.accept(view::showUpdateChecking);
        }

        try {
            backgroundExecutor.execute(() -> runCheck(manual));
        } catch (RuntimeException exception) {
            checkInProgress.set(false);
            if (manual) {
                uiDispatcher.accept(() ->
                        view.showUpdateCheckFailed(
                                conciseMessage(exception)
                        )
                );
            }
        }
    }

    private void runCheck(boolean manual) {
        try {
            UpdatePreferences preferences = preferencesStore.load();
            Instant now = clock.instant();
            if (!manual && !preferences.shouldCheckAutomatically(
                    now,
                    automaticCheckInterval
            )) {
                return;
            }

            UpdateCheckResult result = updateCheckService.check();
            if (result.status() != UpdateCheckResult.Status.CHECK_FAILED) {
                persistSuccessfulCheck(
                        preferences.withSuccessfulCheck(clock.instant())
                );
            }

            applyResult(result, manual);
        } finally {
            checkInProgress.set(false);
        }
    }

    private void applyResult(
            UpdateCheckResult result,
            boolean manual
    ) {
        switch (result.status()) {
            case UPDATE_AVAILABLE -> {
                UpdateManifest manifest = result.manifest().orElseThrow();
                availableRelease.set(manifest.releaseUri());
                uiDispatcher.accept(() ->
                        view.showUpdateAvailable(manifest.version())
                );
            }
            case UP_TO_DATE -> {
                availableRelease.set(null);
                if (manual) {
                    uiDispatcher.accept(view::showUpToDate);
                }
            }
            case CHECK_FAILED -> {
                if (manual) {
                    String message = result.failureMessage()
                            .orElse("Update check failed");
                    uiDispatcher.accept(() ->
                            view.showUpdateCheckFailed(message)
                    );
                }
            }
        }
    }

    private void persistSuccessfulCheck(UpdatePreferences preferences) {
        try {
            preferencesStore.save(preferences);
        } catch (IOException ignored) {
            // Preference persistence must never turn a successful update check
            // into an application-visible failure.
        }
    }

    private void openAvailableRelease() {
        Optional.ofNullable(availableRelease.get())
                .ifPresent(releaseOpener);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
