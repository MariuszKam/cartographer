package cartographer.ui.update;

import cartographer.update.ApplicationVersion;
import cartographer.update.UpdateCheckService;
import cartographer.update.UpdateManifestParser;
import cartographer.update.UpdatePreferences;
import cartographer.update.UpdatePreferencesStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopUpdateControllerTest {

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
                "1.1.0"
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
                "1.1.0"
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
                "1.0.0"
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

    private TestHarness controller(
            UpdatePreferencesStore store,
            Instant now,
            AtomicInteger loads,
            String remoteVersion
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

    private UpdatePreferencesStore store() {
        return new UpdatePreferencesStore(
                temporaryDirectory.resolve("update.properties")
        );
    }

    private static String validManifest(String version) {
        return """
                schemaVersion=1
                channel=stable
                version=%s
                installerFile=VS-Cartographer-Setup-%s.exe
                installerUrl=https://github.com/MariuszKam/cartographer/releases/download/v%s/VS-Cartographer-Setup-%s.exe
                installerSha256=%s
                installerSize=123456
                releaseUrl=https://github.com/MariuszKam/cartographer/releases/tag/v%s
                """.formatted(
                version,
                version,
                version,
                version,
                "a".repeat(64),
                version
        );
    }

    private record TestHarness(
            DesktopUpdateController controller,
            FakeView view
    ) {
    }

    private static final class FakeView implements UpdateCheckView {
        private ApplicationVersion currentVersion;
        private ApplicationVersion availableVersion;
        private boolean checking;
        private boolean upToDate;
        private String failureMessage;
        private Runnable checkAction = () -> { };
        private Runnable openAction = () -> { };

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
        public void showUpdateChecking() {
            checking = true;
        }

        @Override
        public void showUpdateAvailable(ApplicationVersion version) {
            availableVersion = version;
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
