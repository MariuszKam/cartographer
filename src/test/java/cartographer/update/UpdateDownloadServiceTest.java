package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDownloadServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsToPartFileVerifiesAndPromotesInstaller() throws Exception {
        byte[] installerBytes = "verified-installer".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        AtomicReference<Path> sourceDestination = new AtomicReference<>();
        AtomicReference<UpdateDownloadProgress> progress =
                new AtomicReference<>();

        UpdateDownloadService service = new UpdateDownloadService(
                temporaryDirectory.resolve("updates"),
                (requested, destination, listener) -> {
                    sourceDestination.set(destination);
                    Files.write(destination, installerBytes);
                    listener.accept(new UpdateDownloadProgress(
                            installerBytes.length,
                            installerBytes.length
                    ));
                }
        );

        UpdateDownloadResult result = service.download(
                manifest,
                progress::set
        );

        assertEquals(UpdateDownloadResult.Status.READY, result.status());
        Path installer = result.installerPath().orElseThrow();
        assertTrue(sourceDestination.get().toString().endsWith(".exe.part"));
        assertFalse(Files.exists(sourceDestination.get()));
        assertTrue(Files.isRegularFile(installer));
        assertArrayEquals(installerBytes, Files.readAllBytes(installer));
        assertEquals(100, progress.get().percent());
    }

    @Test
    void rejectsWrongHashAndRemovesPartialDownload() throws Exception {
        byte[] expected = "expected-installer".getBytes();
        byte[] corrupt = "corrupt-installer!".getBytes();
        UpdateManifest manifest = manifest(expected);

        UpdateDownloadService service = new UpdateDownloadService(
                temporaryDirectory.resolve("updates"),
                (requested, destination, listener) ->
                        Files.write(destination, corrupt)
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.FAILED, result.status());
        Path versionDirectory = temporaryDirectory
                .resolve("updates")
                .resolve("1.1.0");
        assertFalse(Files.exists(
                versionDirectory.resolve(manifest.installerFile() + ".part")
        ));
        assertFalse(Files.exists(
                versionDirectory.resolve(manifest.installerFile())
        ));
    }

    @Test
    void rejectsWrongSizeAndRemovesPartialDownload() throws Exception {
        byte[] expected = "expected-installer".getBytes();
        UpdateManifest manifest = manifest(expected);

        UpdateDownloadService service = new UpdateDownloadService(
                temporaryDirectory.resolve("updates"),
                (requested, destination, listener) ->
                        Files.write(destination, new byte[]{1, 2, 3})
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.FAILED, result.status());
        assertTrue(
                result.failureMessage().orElseThrow()
                        .contains("verification")
        );
        assertFalse(Files.exists(
                temporaryDirectory.resolve("updates")
                        .resolve("1.1.0")
                        .resolve(manifest.installerFile() + ".part")
        ));
    }

    @Test
    void reusesAlreadyVerifiedInstallerWithoutDownloadingAgain()
            throws Exception {
        byte[] installerBytes = "already-downloaded".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        Path installer = temporaryDirectory.resolve("updates")
                .resolve("1.1.0")
                .resolve(manifest.installerFile());
        Files.createDirectories(installer.getParent());
        Files.write(installer, installerBytes);
        Path stalePartial = installer.resolveSibling(
                installer.getFileName().toString() + ".part"
        );
        Files.write(stalePartial, new byte[]{1, 2, 3});
        AtomicInteger sourceCalls = new AtomicInteger();

        UpdateDownloadService service = new UpdateDownloadService(
                temporaryDirectory.resolve("updates"),
                (requested, destination, listener) ->
                        sourceCalls.incrementAndGet()
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.READY, result.status());
        assertEquals(installer.toAbsolutePath(), result.installerPath()
                .orElseThrow());
        assertEquals(0, sourceCalls.get());
        assertFalse(Files.exists(stalePartial));
    }

    @Test
    void invalidExistingInstallerIsReplacedByVerifiedDownload()
            throws Exception {
        byte[] installerBytes = "fresh-installer".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        Path installer = temporaryDirectory.resolve("updates")
                .resolve("1.1.0")
                .resolve(manifest.installerFile());
        Files.createDirectories(installer.getParent());
        Files.writeString(installer, "old-corrupt-file");
        AtomicInteger sourceCalls = new AtomicInteger();

        UpdateDownloadService service = new UpdateDownloadService(
                temporaryDirectory.resolve("updates"),
                (requested, destination, listener) -> {
                    sourceCalls.incrementAndGet();
                    Files.write(destination, installerBytes);
                }
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.READY, result.status());
        assertEquals(1, sourceCalls.get());
        assertArrayEquals(installerBytes, Files.readAllBytes(installer));
    }

    @Test
    void stagingFilesystemFailureReturnsFailedWithoutStartingTransfer()
            throws Exception {
        byte[] installerBytes = "filesystem-failure".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        Path updates = temporaryDirectory.resolve("updates-as-file");
        Files.writeString(updates, "not-a-directory");
        AtomicInteger sourceCalls = new AtomicInteger();

        UpdateDownloadService service = new UpdateDownloadService(
                updates,
                (requested, destination, listener) ->
                        sourceCalls.incrementAndGet()
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.FAILED, result.status());
        assertEquals(0, sourceCalls.get());
        assertTrue(result.failureMessage().isPresent());
    }

    @Test
    void failedPartialDownloadIsCleanedAndRetryStartsFresh()
            throws Exception {
        byte[] installerBytes = "retry-installer".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        Path updates = temporaryDirectory.resolve("retry-updates");
        AtomicInteger attempts = new AtomicInteger();

        UpdateDownloadService service = new UpdateDownloadService(
                updates,
                (requested, destination, listener) -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        Files.write(destination, new byte[]{1, 2, 3});
                        throw new java.io.IOException("connection reset");
                    }

                    assertFalse(
                            Files.exists(destination),
                            "retry must not reuse stale .part bytes"
                    );
                    Files.write(destination, installerBytes);
                    listener.accept(new UpdateDownloadProgress(
                            installerBytes.length,
                            installerBytes.length
                    ));
                }
        );

        UpdateDownloadResult failed = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.FAILED, failed.status());
        Path partial = updates.resolve("1.1.0")
                .resolve(manifest.installerFile() + ".part");
        assertFalse(Files.exists(partial));

        UpdateDownloadResult retried = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.READY, retried.status());
        assertEquals(2, attempts.get());
        assertArrayEquals(
                installerBytes,
                Files.readAllBytes(retried.installerPath().orElseThrow())
        );
        assertFalse(Files.exists(partial));
    }

    @Test
    void interruptionPreservesInterruptFlagAndCleansPartial()
            throws Exception {
        byte[] installerBytes = "interrupted-installer".getBytes();
        UpdateManifest manifest = manifest(installerBytes);
        Path updates = temporaryDirectory.resolve("updates");

        UpdateDownloadService service = new UpdateDownloadService(
                updates,
                (requested, destination, listener) -> {
                    Files.write(destination, new byte[]{1, 2, 3});
                    throw new InterruptedException("stop");
                }
        );

        try {
            UpdateDownloadResult result = service.download(
                    manifest,
                    ignored -> { }
            );

            assertEquals(UpdateDownloadResult.Status.FAILED, result.status());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(
                    updates.resolve("1.1.0")
                            .resolve(manifest.installerFile() + ".part")
            ));
        } finally {
            Thread.interrupted();
        }
    }

    private UpdateManifest manifest(byte[] installerBytes) throws Exception {
        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(installerBytes)
        );
        String file = "VS-Cartographer-Setup-1.1.0.exe";
        return new UpdateManifest(
                1,
                "stable",
                ApplicationVersion.parse("1.1.0"),
                file,
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "download/v1.1.0/" + file
                ),
                sha256,
                installerBytes.length,
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "tag/v1.1.0"
                )
        );
    }
}
