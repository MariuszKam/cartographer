package cartographer.perf;

import cartographer.cli.CommandException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Observed save revision used to select a render-data cache namespace. */
public record RenderDataCacheRevision(
        RenderDataCacheIdentity identity,
        long saveSize,
        long saveModifiedMillis,
        String schemaVersion,
        String compatibilityVersion
) {
    public RenderDataCacheRevision {
        Objects.requireNonNull(identity, "identity is required");
        if (saveSize < 0) {
            throw new IllegalArgumentException("save size cannot be negative");
        }
        schemaVersion = requireText(schemaVersion, "schema version");
        compatibilityVersion = requireText(compatibilityVersion, "compatibility version");
    }

    public static RenderDataCacheRevision observe(
            Path savePath,
            String schemaVersion,
            String compatibilityVersion
    ) {
        RenderDataCacheIdentity identity = new RenderDataCacheIdentity(savePath);
        try {
            return new RenderDataCacheRevision(
                    identity,
                    Files.size(identity.normalizedSavePath()),
                    Files.getLastModifiedTime(identity.normalizedSavePath()).toMillis(),
                    schemaVersion,
                    compatibilityVersion
            );
        } catch (IOException exception) {
            throw new CommandException(
                    "Cannot inspect save revision: " + exception.getMessage(),
                    exception
            );
        }
    }

    public String revisionHash() {
        return RenderDataCacheIdentityHash.sha256(
                identity.namespaceHash()
                        + "\n" + saveSize
                        + "\n" + saveModifiedMillis
                        + "\n" + schemaVersion
                        + "\n" + compatibilityVersion
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
