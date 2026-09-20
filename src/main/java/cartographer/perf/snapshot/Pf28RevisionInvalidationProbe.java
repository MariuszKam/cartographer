package cartographer.perf.snapshot;

import cartographer.model.WorldMetadata;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldSnapshotHeader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic PF-2.8 proof that one derived snapshot revision cannot leak
 * into a changed source revision.
 */
final class Pf28RevisionInvalidationProbe {

    boolean verify(Path root) {
        try {
            Files.createDirectories(root);
            Path source = root.resolve("revision-source.bin");
            Path cache = root.resolve("cache");
            Files.writeString(
                    source,
                    "revision-a",
                    StandardCharsets.UTF_8
            );

            RenderDataCacheStore store =
                    new RenderDataCacheStore(cache);
            WorldDataSnapshot first =
                    WorldDataSnapshot.openOrCreate(store, source)
                            .orElseThrow();
            first.headerStore().publish(markerHeader());

            if (first.headerStore().read().isEmpty()) {
                return false;
            }

            String firstHash = first.revisionHash();
            Files.writeString(
                    source,
                    "revision-b-with-different-size",
                    StandardCharsets.UTF_8
            );

            // The changed source revision must not resolve to the published
            // namespace before a new derived namespace is explicitly created.
            if (WorldDataSnapshot.openExisting(store, source).isPresent()) {
                return false;
            }

            WorldDataSnapshot second =
                    WorldDataSnapshot.openOrCreate(store, source)
                            .orElseThrow();

            return !firstHash.equals(second.revisionHash())
                    && first.headerStore().read().isPresent()
                    && second.headerStore().read().isEmpty();
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private WorldSnapshotHeader markerHeader() {
        return new WorldSnapshotHeader(
                new WorldMetadata(32, 64, 32),
                Map.of(),
                Optional.empty()
        );
    }
}
