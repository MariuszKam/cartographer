package cartographer.snapshot;

import cartographer.cache.RenderDataCacheRevision;
import cartographer.cache.RenderDataCacheStore;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Small revision-local status file for world snapshot preparation UX.
 */
public final class WorldSnapshotPreparationSummaryStore {
    private static final String FILE_NAME =
            "world-preparation-summary.properties";
    private final RenderDataCacheStore cacheStore;
    private final RenderDataCacheRevision revision;
    private final Path path;

    public WorldSnapshotPreparationSummaryStore(
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

    public Optional<WorldSnapshotPreparationSummary> read() {
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
            WorldSnapshotPreparationSummary summary =
                    new WorldSnapshotPreparationSummary(
                            required(properties, "revisionHash"),
                            Integer.parseInt(
                                    required(properties, "observedMapChunks")
                            ),
                            flag(properties, "mapChunkCatalogComplete"),
                            flag(properties, "terrainCoverageComplete"),
                            flag(properties, "surfaceCoverageComplete"),
                            flag(properties, "mapRegionCoverageComplete"),
                            flag(properties, "upperRockCoverageComplete"),
                            flag(properties, "resourceIndexCoverageComplete")
                    );
            return summary.revisionHash().equals(revision.revisionHash())
                    ? Optional.of(summary)
                    : Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    public void publish(WorldSnapshotPreparationSummary summary) {
        Objects.requireNonNull(summary, "summary is required");
        if (!summary.revisionHash().equals(revision.revisionHash())) {
            throw new IllegalArgumentException(
                    "preparation summary revision does not match store revision"
            );
        }
        if (cacheStore.find(revision).isEmpty()) {
            throw new IllegalStateException(
                    "preparation summary requires a compatible cache manifest"
            );
        }
        try {
            Files.createDirectories(path.getParent());
            String text = "revisionHash=" + summary.revisionHash() + "\n"
                    + "observedMapChunks=" + summary.observedMapChunks() + "\n"
                    + "mapChunkCatalogComplete="
                    + summary.mapChunkCatalogComplete() + "\n"
                    + "terrainCoverageComplete="
                    + summary.terrainCoverageComplete() + "\n"
                    + "surfaceCoverageComplete="
                    + summary.surfaceCoverageComplete() + "\n"
                    + "mapRegionCoverageComplete="
                    + summary.mapRegionCoverageComplete() + "\n"
                    + "upperRockCoverageComplete="
                    + summary.upperRockCoverageComplete() + "\n"
                    + "resourceIndexCoverageComplete="
                    + summary.resourceIndexCoverageComplete() + "\n";
            Path temporary = Files.createTempFile(
                    path.getParent(),
                    ".world-preparation-",
                    ".tmp"
            );
            try {
                Files.writeString(
                        temporary,
                        text,
                        StandardCharsets.UTF_8
                );
                try {
                    Files.move(
                            temporary,
                            path,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(
                            temporary,
                            path,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot publish world preparation summary: "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private static String required(
            Properties properties,
            String key
    ) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "missing preparation summary property: " + key
            );
        }
        return value.trim();
    }

    private static boolean flag(
            Properties properties,
            String key
    ) {
        String value = required(properties, key);
        if (!"true".equalsIgnoreCase(value)
                && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(
                    "invalid boolean preparation summary property: " + key
            );
        }
        return Boolean.parseBoolean(value);
    }
}
