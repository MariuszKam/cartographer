package cartographer.ui.update;

import cartographer.update.ApplicationVersion;
import cartographer.update.UpdateCheckService;
import cartographer.update.UpdateDownloadProgress;
import cartographer.update.UpdateDownloadService;
import cartographer.update.UpdateManifestParser;
import cartographer.update.UpdatePreferences;
import cartographer.update.UpdatePreferencesStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopUpdateControllerTest {
    private static final byte[] INSTALLER_BYTES =
            "controller-installer".getBytes();

    @TempDir
    Path temporaryDirectory;

    @Test
    void automaticCheckHonorsTwentyFourHourTtl() throws Exception {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        UpdatePreferencesStore store = store();
        store.save(new UpdatePreferences(
                true,
                Optional.of(now.minus(Duration.ofHours(1)))
        ));
        AtomicInteger loads = new AtomicInteger();

        TestHarness harness = controller(
                store,
                now,
                loads,
                "1.1.0",
                successfulDownloadService()
        );

        harness.controller.startAutomaticCheck();

        assertEquals(0, loads.get());
        assertNull(harness.view.availableVersion);
    }

    @Test
    void automaticCheckShowsAvailableUpdateAndPersistsSuccess()
            throws Exception {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        UpdatePreferencesStore store = store();
        AtomicInteger loads = new AtomicInteger();
        TestHarness harness = controller(
                store,
                now,
                loads,
                "1.1.0",
                successfulDownloadService()
        );

        harness.controller.startAutomaticCheck();

        assertEquals(1, loads.get());
        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                harness.view.availableVersion
        );
        assertEquals(
                Optional.of(now),
                store.load().lastSuccessfulCheck()
        );
    }

    @Test
    void manualCheckBypassesTtl() throws Exception {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        UpdatePreferencesStore store = store();
        store.save(new UpdatePreferences(
                true,
                Optional.of(now.minus(Duration.ofHours(1)))
        ));
        AtomicInteger loads = new AtomicInteger();
        TestHarness harness = controller(
                store,
                now,
                loads,
                "1.0.0",
                successfulDownloadService()
        );

        harness.controller.checkNow();

        assertEquals(1, loads.get());
        assertTrue(harness.view.upToDate);
        assertTrue(harness.view.checking);
    }

    @Test
    void failedAutomaticCheckIsSilentAndDoesNotAdvanceTtl() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        UpdatePreferencesStore store = store();
        AtomicInteger loads = new AtomicInteger();
        FakeView view = new FakeView();
        UpdateCheckService service = new UpdateCheckService(
                ApplicationVersion.parse("1.0.0"),
                () -> {
                    loads.incrementAndGet();
                    throw new java.io.IOException("offline");
                },
                new UpdateManifestParser()
        );
        DesktopUpdateController controller = new DesktopUpdateController(
                service,
                successfulDownloadService(),
                store,
                view,
                Runnable::run,
                Runnable::run,
                ignored -> { },
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(24)
        );

        controller.startAutomaticCheck();

        assertEquals(1, loads.get());
        assertNull(view.failureMessage);
        assertTrue(store.load().lastSuccessfulCheck().isEmpty());
    }

    @Test
    void availableChipOpensReleaseUrl() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<URI> opened = new AtomicReference<>();
        FakeView view = new FakeView();
        UpdateCheckService service = new UpdateCheckService(
                ApplicationVersion.parse("1.0.0"),
                () -> {
                    loads.incrementAndGet();
                    return validManifest("1.1.0");
                },
                new UpdateManifestParser()
        );
        DesktopUpdateController controller = new DesktopUpdateController(
                service,
                successfulDownloadService(),
                store(),
                view,
                Runnable::run,
                Runnable::run,
                opened::set,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(24)
        );

        controller.checkNow();
        view.openAction.run();

        assertEquals(1, loads.get());
        assertEquals(
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/tag/v1.1.0"
                ),
                opened.get()
        );
    }

    @Test
    void downloadActionPublishesProgressAndReadyState() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicInteger loads = new AtomicInteger();
        TestHarness harness = controller(
                store(),
                now,
                loads,
                "1.1.0",
                successfulDownloadService()
        );

        harness.controller.checkNow();
        harness.view.downloadAction.run();

        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                harness.view.readyVersion
        );
        assertEquals(100, harness.view.downloadPercent);
        assertNull(harness.view.downloadFailure);
    }

    @Test
    void failedDownloadCanBeRetriedAndReachReadyState() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        UpdateDownloadService retrying = new UpdateDownloadService(
                temporaryDirectory.resolve("retry-updates"),
                (manifest, destination, listener) -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new java.io.IOException("temporary outage");
                    }
                    Files.write(destination, INSTALLER_BYTES);
                    listener.accept(new UpdateDownloadProgress(
                            INSTALLER_BYTES.length,
                            INSTALLER_BYTES.length
                    ));
                }
        );
        TestHarness harness = controller(
                store(),
                now,
                loads,
                "1.1.0",
                retrying
        );

        harness.controller.checkNow();
        harness.view.downloadAction.run();
        assertTrue(
                harness.view.downloadFailure.contains("temporary outage")
        );

        harness.view.downloadAction.run();

        assertEquals(2, attempts.get());
        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                harness.view.readyVersion
        );
    }

    @Test
    void queuedDownloadBlocksOverlappingChecksAndDuplicateDownloads() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        Queue<Runnable> backgroundTasks = new ArrayDeque<>();
        FakeView view = new FakeView();

        UpdateCheckService service = new UpdateCheckService(
                ApplicationVersion.parse("1.0.0"),
                () -> {
                    loads.incrementAndGet();
                    return validManifest("1.1.0");
                },
                new UpdateManifestParser()
        );
        UpdateDownloadService downloadService = new UpdateDownloadService(
                temporaryDirectory.resolve("serialized-updates"),
                (manifest, destination, listener) -> {
                    downloads.incrementAndGet();
                    Files.write(destination, INSTALLER_BYTES);
                    listener.accept(new UpdateDownloadProgress(
                            INSTALLER_BYTES.length,
                            INSTALLER_BYTES.length
                    ));
                }
        );
        DesktopUpdateController controller = new DesktopUpdateController(
                service,
                downloadService,
                store(),
                view,
                backgroundTasks::add,
                Runnable::run,
                ignored -> { },
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(24)
        );

        controller.checkNow();
        assertEquals(1, backgroundTasks.size());
        backgroundTasks.remove().run();
        assertEquals(1, loads.get());

        view.downloadAction.run();
        assertEquals(1, backgroundTasks.size());

        view.downloadAction.run();
        controller.checkNow();

        assertEquals(1, backgroundTasks.size());
        assertEquals(1, loads.get());
        assertEquals(0, downloads.get());

        backgroundTasks.remove().run();

        assertEquals(1, downloads.get());
        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                view.readyVersion
        );
    }

    @Test
    void failedDownloadShowsRetryStateWithoutLosingAvailableUpdate() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicInteger loads = new AtomicInteger();
        UpdateDownloadService failing = new UpdateDownloadService(
                temporaryDirectory.resolve("failed-updates"),
                (manifest, destination, listener) -> {
                    throw new java.io.IOException("network lost");
                }
        );
        TestHarness harness = controller(
                store(),
                now,
                loads,
                "1.1.0",
                failing
        );

        harness.controller.checkNow();
        harness.view.downloadAction.run();

        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                harness.view.availableVersion
        );
        assertNull(harness.view.readyVersion);
        assertTrue(harness.view.downloadFailure.contains("network lost"));
    }

    private TestHarness controller(
            UpdatePreferencesStore store,
            Instant now,
            AtomicInteger loads,
            String remoteVersion,
            UpdateDownloadService downloadService
    ) {
        FakeView view = new FakeView();
        UpdateCheckService service = new UpdateCheckService(
                ApplicationVersion.parse("1.0.0"),
                () -> {
                    loads.incrementAndGet();
                    return validManifest(remoteVersion);
                },
                new UpdateManifestParser()
        );
        DesktopUpdateController controller = new DesktopUpdateController(
                service,
                downloadService,
                store,
                view,
                Runnable::run,
                Runnable::run,
                ignored -> { },
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(24)
        );
        return new TestHarness(controller, view);
    }

    private UpdateDownloadService successfulDownloadService() {
        return new UpdateDownloadService(
                temporaryDirectory.resolve("updates-" + System.nanoTime()),
                (manifest, destination, listener) -> {
                    Files.write(destination, INSTALLER_BYTES);
                    listener.accept(new UpdateDownloadProgress(
                            INSTALLER_BYTES.length,
                            INSTALLER_BYTES.length
                    ));
                }
        );
    }

    private UpdatePreferencesStore store() {
        return new UpdatePreferencesStore(
                temporaryDirectory.resolve(
                        "update-" + System.nanoTime() + ".properties"
                )
        );
    }

    private static String validManifest(String version) {
        String file = "VS-Cartographer-Setup-" + version + ".exe";
        return """
                schemaVersion=1
                channel=stable
                version=%s
                installerFile=%s
                installerUrl=https://github.com/MariuszKam/cartographer/releases/download/v%s/%s
                installerSha256=%s
                installerSize=%d
                releaseUrl=https://github.com/MariuszKam/cartographer/releases/tag/v%s
                """.formatted(
                version,
                file,
                version,
                file,
                sha256(INSTALLER_BYTES),
                INSTALLER_BYTES.length,
                version
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestHarness(
            DesktopUpdateController controller,
            FakeView view
    ) {
    }

    private static final class FakeView implements UpdateCheckView {
        private ApplicationVersion currentVersion;
        private ApplicationVersion availableVersion;
        private ApplicationVersion readyVersion;
        private boolean checking;
        private boolean upToDate;
        private int downloadPercent = -1;
        private String failureMessage;
        private String downloadFailure;
        private Runnable checkAction = () -> { };
        private Runnable openAction = () -> { };
        private Runnable downloadAction = () -> { };

        @Override
        public void showCurrentVersion(ApplicationVersion version) {
            currentVersion = version;
        }

        @Override
        public void setOnCheckForUpdates(Runnable action) {
            checkAction = action;
        }

        @Override
        public void setOnOpenUpdateRelease(Runnable action) {
            openAction = action;
        }

        @Override
        public void setOnDownloadUpdate(Runnable action) {
            downloadAction = action;
        }

        @Override
        public void showUpdateChecking() {
            checking = true;
        }

        @Override
        public void showUpdateAvailable(ApplicationVersion version) {
            availableVersion = version;
        }

        @Override
        public void showUpdateDownloading(
                ApplicationVersion version,
                int percent
        ) {
            availableVersion = version;
            downloadPercent = percent;
        }

        @Override
        public void showUpdateReady(ApplicationVersion version) {
            readyVersion = version;
            downloadFailure = null;
        }

        @Override
        public void showUpdateDownloadFailed(
                ApplicationVersion version,
                String message
        ) {
            availableVersion = version;
            downloadFailure = message;
        }

        @Override
        public void showUpToDate() {
            upToDate = true;
        }

        @Override
        public void showUpdateCheckFailed(String message) {
            failureMessage = message;
        }
    }
}
