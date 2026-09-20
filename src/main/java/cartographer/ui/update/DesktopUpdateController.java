package cartographer.ui.update;

import cartographer.update.UpdateCheckResult;
import cartographer.update.UpdateCheckService;
import cartographer.update.UpdateDownloadProgress;
import cartographer.update.UpdateDownloadResult;
import cartographer.update.UpdateDownloadService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DesktopUpdateController {
    public static final Duration DEFAULT_AUTOMATIC_CHECK_INTERVAL =
            Duration.ofHours(24);

    private final UpdateCheckService updateCheckService;
    private final UpdateDownloadService updateDownloadService;
    private final UpdatePreferencesStore preferencesStore;
    private final UpdateCheckView view;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiDispatcher;
    private final Consumer<URI> releaseOpener;
    private final Clock clock;
    private final Duration automaticCheckInterval;
    private final AtomicBoolean operationInProgress = new AtomicBoolean();
    private final AtomicReference<UpdateManifest> availableUpdate =
            new AtomicReference<>();
    private final AtomicReference<UpdateDownloadResult> readyUpdate =
            new AtomicReference<>();

    public DesktopUpdateController(
            UpdateCheckService updateCheckService,
            UpdateDownloadService updateDownloadService,
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
        this.updateDownloadService = Objects.requireNonNull(
                updateDownloadService,
                "updateDownloadService is required"
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
        view.setOnDownloadUpdate(this::downloadAvailableUpdate);
    }

    public void startAutomaticCheck() {
        submitCheck(false);
    }

    public void checkNow() {
        submitCheck(true);
    }

    private void submitCheck(boolean manual) {
        if (!operationInProgress.compareAndSet(false, true)) {
            return;
        }

        if (manual) {
            uiDispatcher.accept(view::showUpdateChecking);
        }

        try {
            backgroundExecutor.execute(() -> runCheck(manual));
        } catch (RuntimeException exception) {
            operationInProgress.set(false);
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

            applyCheckResult(result, manual);
        } finally {
            operationInProgress.set(false);
        }
    }

    private void applyCheckResult(
            UpdateCheckResult result,
            boolean manual
    ) {
        switch (result.status()) {
            case UPDATE_AVAILABLE -> {
                UpdateManifest manifest = result.manifest().orElseThrow();
                availableUpdate.set(manifest);

                UpdateDownloadResult ready = readyUpdate.get();
                if (ready != null && ready.manifest().equals(manifest)) {
                    uiDispatcher.accept(() ->
                            view.showUpdateReady(manifest.version())
                    );
                } else {
                    readyUpdate.set(null);
                    uiDispatcher.accept(() ->
                            view.showUpdateAvailable(manifest.version())
                    );
                }
            }
            case UP_TO_DATE -> {
                availableUpdate.set(null);
                readyUpdate.set(null);
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

    private void downloadAvailableUpdate() {
        UpdateManifest manifest = availableUpdate.get();
        if (manifest == null
                || !operationInProgress.compareAndSet(false, true)) {
            return;
        }

        UpdateDownloadResult ready = readyUpdate.get();
        if (ready != null && ready.manifest().equals(manifest)) {
            operationInProgress.set(false);
            uiDispatcher.accept(() ->
                    view.showUpdateReady(manifest.version())
            );
            return;
        }

        uiDispatcher.accept(() ->
                view.showUpdateDownloading(manifest.version(), 0)
        );

        try {
            backgroundExecutor.execute(() -> runDownload(manifest));
        } catch (RuntimeException exception) {
            operationInProgress.set(false);
            uiDispatcher.accept(() ->
                    view.showUpdateDownloadFailed(
                            manifest.version(),
                            conciseMessage(exception)
                    )
            );
        }
    }

    private void runDownload(UpdateManifest manifest) {
        AtomicInteger lastPercent = new AtomicInteger(-1);
        try {
            UpdateDownloadResult result = updateDownloadService.download(
                    manifest,
                    progress -> publishProgress(
                            manifest,
                            progress,
                            lastPercent
                    )
            );

            if (result.status() == UpdateDownloadResult.Status.READY) {
                readyUpdate.set(result);
                uiDispatcher.accept(() ->
                        view.showUpdateReady(manifest.version())
                );
            } else {
                readyUpdate.set(null);
                String message = result.failureMessage()
                        .orElse("Update download failed");
                uiDispatcher.accept(() ->
                        view.showUpdateDownloadFailed(
                                manifest.version(),
                                message
                        )
                );
            }
        } finally {
            operationInProgress.set(false);
        }
    }

    private void publishProgress(
            UpdateManifest manifest,
            UpdateDownloadProgress progress,
            AtomicInteger lastPercent
    ) {
        int percent = progress.percent();
        if (lastPercent.getAndSet(percent) == percent) {
            return;
        }
        uiDispatcher.accept(() ->
                view.showUpdateDownloading(manifest.version(), percent)
        );
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
        Optional.ofNullable(availableUpdate.get())
                .map(UpdateManifest::releaseUri)
                .ifPresent(releaseOpener);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
