package cartographer.perf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Cache-local persistence for the last successful PF-2 preparation summary.
 */
public final class WorldPreparationStateStore {
    private static final String FILE_NAME =
            "world-preparation-state-v1.properties";
    private static final String VERSION = "1";

    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path path;

    public WorldPreparationStateStore(
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

    public Optional<WorldPreparationState> read() {
        if (cacheStore.find(revision).isEmpty()
                || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
            if (!VERSION.equals(properties.getProperty("version"))) {
                return Optional.empty();
            }
            String revisionHash = properties.getProperty("revisionHash");
            if (!revision.revisionHash().equals(revisionHash)) {
                return Optional.empty();
            }
            return Optional.of(new WorldPreparationState(
                    revisionHash,
                    integer(properties, "observedMapChunks"),
                    flag(properties, "mapChunkCatalogComplete"),
                    flag(properties, "terrainCoverageComplete"),
                    flag(properties, "surfaceCoverageComplete"),
                    flag(properties, "mapRegionCoverageComplete"),
                    flag(properties, "upperRockCoverageComplete"),
                    flag(properties, "resourceIndexCoverageComplete")
            ));
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    public void publish(WorldPreparationState state) {
        Objects.requireNonNull(state, "state is required");
        if (!revision.revisionHash().equals(state.revisionHash())) {
            throw new IllegalArgumentException(
                    "preparation state revision does not match store revision"
            );
        }
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "preparation state requires a compatible published manifest"
            );
        }

        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("revisionHash", state.revisionHash());
        properties.setProperty(
                "observedMapChunks",
                Integer.toString(state.observedMapChunks())
        );
        properties.setProperty(
                "mapChunkCatalogComplete",
                Boolean.toString(state.mapChunkCatalogComplete())
        );
        properties.setProperty(
                "terrainCoverageComplete",
                Boolean.toString(state.terrainCoverageComplete())
        );
        properties.setProperty(
                "surfaceCoverageComplete",
                Boolean.toString(state.surfaceCoverageComplete())
        );
        properties.setProperty(
                "mapRegionCoverageComplete",
                Boolean.toString(state.mapRegionCoverageComplete())
        );
        properties.setProperty(
                "upperRockCoverageComplete",
                Boolean.toString(state.upperRockCoverageComplete())
        );
        properties.setProperty(
                "resourceIndexCoverageComplete",
                Boolean.toString(state.resourceIndexCoverageComplete())
        );

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8
            )) {
                properties.store(
                        writer,
                        "VS Cartographer PF-2 world preparation state"
                );
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot publish world preparation state: "
                            + failure.getMessage(),
                    failure
            );
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Missing summary remains safely non-authoritative.
            }
        }
    }

    private static int integer(Properties properties, String key) {
        String value = required(properties, key);
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(
                    key + " cannot be negative"
            );
        }
        return parsed;
    }

    private static boolean flag(Properties properties, String key) {
        String value = required(properties, key);
        if (!"true".equalsIgnoreCase(value)
                && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(
                    key + " must be true or false"
            );
        }
        return Boolean.parseBoolean(value);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "missing preparation-state property: " + key
            );
        }
        return value.trim();
    }
}
