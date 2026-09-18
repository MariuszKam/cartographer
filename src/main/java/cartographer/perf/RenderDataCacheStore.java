package cartographer.perf;

import cartographer.cli.CommandException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** Persistent manifest store for the PF-1.7 render-data cache foundation. */
public final class RenderDataCacheStore {
    private static final String MANIFEST_FILE = "manifest.properties";

    private final Path cacheRoot;

    public RenderDataCacheStore(Path cacheRoot) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cache root is required")
                .toAbsolutePath()
                .normalize();
    }

    public Path cacheRoot() {
        return cacheRoot;
    }

    public RenderDataCacheRevision observe(Path savePath) {
        return RenderDataCacheRevision.observe(
                savePath,
                RenderDataCacheManifest.CURRENT_SCHEMA_VERSION,
                RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION
        );
    }

    /** Returns a compatible manifest, or an empty result for any cache miss. */
    public Optional<RenderDataCacheManifest> find(RenderDataCacheRevision revision) {
        Objects.requireNonNull(revision, "revision is required");
        if (!isCurrentFormat(revision)) {
            return Optional.empty();
        }
        Path manifestPath = manifestPath(revision);
        if (!Files.isRegularFile(manifestPath)) {
            return Optional.empty();
        }
        try {
            RenderDataCacheManifest manifest = RenderDataCacheManifest.parse(
                    Files.readString(manifestPath, StandardCharsets.UTF_8)
            );
            return manifest.matches(revision)
                    ? Optional.of(manifest)
                    : Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Publishes only complete, current-format manifest metadata. */
    public void publish(RenderDataCacheRevision revision) {
        Objects.requireNonNull(revision, "revision is required");
        if (!isCurrentFormat(revision)) {
            throw new IllegalArgumentException("cannot publish an incompatible render-data revision");
        }
        Path directory = revisionDirectory(revision);
        Path destination = manifestPath(revision);
        if (Files.isRegularFile(destination)) {
            return;
        }
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".manifest-", ".tmp");
            byte[] content = new RenderDataCacheManifest(revision)
                    .serialize()
                    .getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            boolean published = false;
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE
                );
                published = true;
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, destination);
                    published = true;
                } catch (FileAlreadyExistsException alreadyPublished) {
                    // Another writer published the same immutable revision.
                }
            } catch (FileAlreadyExistsException alreadyPublished) {
                // Another writer published the same immutable revision.
            }
            if (published) {
                temporary = null;
            }
        } catch (IOException exception) {
            throw new CommandException(
                    "Cannot publish render-data cache manifest: " + exception.getMessage(),
                    exception
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The completed cache manifest remains absent and is a miss.
                }
            }
        }
    }

    public Path manifestPath(RenderDataCacheRevision revision) {
        Objects.requireNonNull(revision, "revision is required");
        return revisionDirectory(revision).resolve(MANIFEST_FILE);
    }

    private Path revisionDirectory(RenderDataCacheRevision revision) {
        return cacheRoot
                .resolve(revision.identity().namespaceHash())
                .resolve(revision.revisionHash());
    }

    private boolean isCurrentFormat(RenderDataCacheRevision revision) {
        return RenderDataCacheManifest.CURRENT_SCHEMA_VERSION.equals(revision.schemaVersion())
                && RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION
                .equals(revision.compatibilityVersion());
    }
}
