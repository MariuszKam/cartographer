package cartographer.perf;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Revision-scoped persistent codec/store for {@link WorldSnapshotHeader}. */
public final class WorldSnapshotHeaderStore {
    private static final String FILE_NAME = "world-snapshot-header-v1.bin";
    private static final int MAGIC = 0x56534348; // VSCH
    private static final int VERSION = 1;
    private static final int MAX_REGISTRY_ENTRIES = 10_000_000;
    private static final int MAX_STRING_BYTES = 1_048_576;

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path path;

    public WorldSnapshotHeaderStore(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
        this.revision = Objects.requireNonNull(
                revision,
                "revision is required"
        );
        this.path = cacheStore.manifestPath(revision)
                .getParent()
                .resolve(FILE_NAME);
    }

    public Path path() {
        return path;
    }

    public Optional<WorldSnapshotHeader> read() {
        if (cacheStore.find(revision).isEmpty()
                || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path))
        )) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                return Optional.empty();
            }
            int sizeX = input.readInt();
            int sizeY = input.readInt();
            int sizeZ = input.readInt();
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                return Optional.empty();
            }

            Optional<WorldPosition> player;
            if (input.readBoolean()) {
                double x = input.readDouble();
                double y = input.readDouble();
                double z = input.readDouble();
                if (!Double.isFinite(x)
                        || !Double.isFinite(y)
                        || !Double.isFinite(z)) {
                    return Optional.empty();
                }
                player = Optional.of(new WorldPosition(x, y, z));
            } else {
                player = Optional.empty();
            }

            int entries = input.readInt();
            if (entries < 0 || entries > MAX_REGISTRY_ENTRIES) {
                return Optional.empty();
            }
            LinkedHashMap<Integer, BlockInfo> registry =
                    new LinkedHashMap<>(Math.min(entries, 65_536));
            for (int index = 0; index < entries; index++) {
                int id = input.readInt();
                String code = readNullableString(input);
                if (registry.put(id, new BlockInfo(id, code)) != null) {
                    return Optional.empty();
                }
            }
            if (input.read() != -1) {
                return Optional.empty();
            }
            return Optional.of(new WorldSnapshotHeader(
                    new WorldMetadata(sizeX, sizeY, sizeZ),
                    registry,
                    player
            ));
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    public void publish(WorldSnapshotHeader header) {
        Objects.requireNonNull(header, "header is required");
        requirePublishedRevision();
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(
                    path.getFileName() + ".tmp"
            );
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary))
            )) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(header.metadata().mapSizeX());
                output.writeInt(header.metadata().mapSizeY());
                output.writeInt(header.metadata().mapSizeZ());
                output.writeBoolean(header.player().isPresent());
                if (header.player().isPresent()) {
                    WorldPosition player = header.player().orElseThrow();
                    output.writeDouble(player.x());
                    output.writeDouble(player.y());
                    output.writeDouble(player.z());
                }

                var entries = header.blockRegistry().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
                output.writeInt(entries.size());
                for (Map.Entry<Integer, BlockInfo> entry : entries) {
                    output.writeInt(entry.getKey());
                    writeNullableString(output, entry.getValue().code());
                }
            }
            moveIntoPlace(temporary, path);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot publish world snapshot header: "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private void requirePublishedRevision() {
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "world snapshot header requires a compatible manifest"
            );
        }
    }

    private static String readNullableString(DataInputStream input)
            throws IOException {
        int length = input.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new EOFException("invalid snapshot-header string length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("truncated snapshot-header string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeNullableString(
            DataOutputStream output,
            String value
    ) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("snapshot-header string is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void moveIntoPlace(Path from, Path to)
            throws IOException {
        try {
            Files.move(
                    from,
                    to,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(
                    from,
                    to,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}
