package cartographer.perf.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Validated filesystem layout for one PF-2.8 evidence campaign.
 *
 * <p>The evidence directory may be newly absent or already exist as an empty
 * directory. Reusing non-empty evidence is rejected so results from different
 * candidate SHAs/campaigns cannot be mixed accidentally.</p>
 */
final class Pf28ValidationPaths {
    private final Path save;
    private final Path outputRoot;

    private Pf28ValidationPaths(Path save, Path outputRoot) {
        this.save = save;
        this.outputRoot = outputRoot;
    }

    static Pf28ValidationPaths prepare(
            Path savePath,
            Path outputRoot
    ) {
        Path save = Objects.requireNonNull(
                savePath,
                "savePath is required"
        ).toAbsolutePath().normalize();
        if (!Files.isRegularFile(save)) {
            throw new IllegalArgumentException(
                    "save must be an existing regular file: " + save
            );
        }

        Path root = Objects.requireNonNull(
                outputRoot,
                "outputRoot is required"
        ).toAbsolutePath().normalize();
        Path sourceDirectory = Objects.requireNonNull(
                save.getParent(),
                "save parent is required"
        ).toAbsolutePath().normalize();

        if (root.equals(save)
                || root.startsWith(sourceDirectory)
                || sourceDirectory.startsWith(root)) {
            throw new IllegalArgumentException(
                    "PF-2.8 outputRoot must be isolated from the source save directory: "
                            + root
            );
        }

        try {
            if (Files.exists(root)) {
                if (!Files.isDirectory(root)) {
                    throw new IllegalArgumentException(
                            "PF-2.8 outputRoot must be a directory: " + root
                    );
                }
                try (var entries = Files.list(root)) {
                    if (entries.findAny().isPresent()) {
                        throw new IllegalArgumentException(
                                "PF-2.8 requires a fresh empty evidence directory: "
                                        + root
                        );
                    }
                }
            } else {
                Files.createDirectories(root);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot prepare PF-2.8 output root: " + root,
                    exception
            );
        }

        return new Pf28ValidationPaths(save, root);
    }

    Path save() {
        return save;
    }

    Path outputRoot() {
        return outputRoot;
    }

    Path cacheRoot() {
        return outputRoot.resolve("snapshot-cache");
    }

    Path renderStateRoot() {
        return outputRoot.resolve("render-state");
    }

    Path revisionProbeRoot() {
        return outputRoot.resolve("revision-probe");
    }
}
