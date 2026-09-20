package cartographer.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Consumer;

public final class UpdateDownloadService {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private final Path updatesRoot;
    private final UpdateInstallerSource installerSource;

    public UpdateDownloadService(
            Path updatesRoot,
            UpdateInstallerSource installerSource
    ) {
        this.updatesRoot = Objects.requireNonNull(
                updatesRoot,
                "updatesRoot is required"
        ).toAbsolutePath().normalize();
        this.installerSource = Objects.requireNonNull(
                installerSource,
                "installerSource is required"
        );
    }

    public UpdateDownloadResult download(
            UpdateManifest manifest,
            Consumer<UpdateDownloadProgress> progressListener
    ) {
        Objects.requireNonNull(manifest, "manifest is required");
        Objects.requireNonNull(
                progressListener,
                "progressListener is required"
        );

        Path partial = null;
        try {
            Path versionDirectory = safeVersionDirectory(manifest);
            Path installer = safeChild(
                    versionDirectory,
                    manifest.installerFile()
            );
            partial = safeChild(
                    versionDirectory,
                    manifest.installerFile() + ".part"
            );

            Files.createDirectories(versionDirectory);

            if (isVerified(installer, manifest)) {
                deleteQuietly(partial);
                progressListener.accept(new UpdateDownloadProgress(
                        manifest.installerSize(),
                        manifest.installerSize()
                ));
                return UpdateDownloadResult.ready(manifest, installer);
            }

            Files.deleteIfExists(installer);
            Files.deleteIfExists(partial);
            ensureSpaceAvailable(versionDirectory, manifest.installerSize());

            installerSource.download(
                    manifest,
                    partial,
                    progressListener
            );

            if (!isVerified(partial, manifest)) {
                throw new IOException(
                        "Downloaded installer failed size or SHA-256 verification"
                );
            }

            promote(partial, installer);
            return UpdateDownloadResult.ready(manifest, installer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deleteQuietly(partial);
            return UpdateDownloadResult.failed(
                    manifest,
                    "Installer download was interrupted"
            );
        } catch (Exception exception) {
            deleteQuietly(partial);
            return UpdateDownloadResult.failed(
                    manifest,
                    conciseMessage(exception)
            );
        }
    }

    private Path safeVersionDirectory(UpdateManifest manifest) {
        Path directory = updatesRoot.resolve(
                manifest.version().toString()
        ).normalize();
        if (!directory.startsWith(updatesRoot)) {
            throw new IllegalArgumentException(
                    "Update version resolves outside the update directory"
            );
        }
        return directory;
    }

    private Path safeChild(Path directory, String fileName) {
        Path path = directory.resolve(fileName).normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Update file resolves outside the version directory"
            );
        }
        return path;
    }

    private void ensureSpaceAvailable(
            Path versionDirectory,
            long requiredBytes
    ) throws IOException {
        long usable = Files.getFileStore(versionDirectory).getUsableSpace();
        if (usable < requiredBytes) {
            throw new IOException(
                    "Not enough free disk space for the update"
            );
        }
    }

    private boolean isVerified(
            Path file,
            UpdateManifest manifest
    ) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (Files.size(file) != manifest.installerSize()) {
            return false;
        }
        return manifest.installerSha256().equals(sha256(file));
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void promote(Path partial, Path installer) throws IOException {
        try {
            Files.move(
                    partial,
                    installer,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    partial,
                    installer,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later retry attempts cleanup again before downloading.
        }
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
