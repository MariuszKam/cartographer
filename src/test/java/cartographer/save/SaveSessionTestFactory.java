package cartographer.save;

import cartographer.model.WorldMetadata;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Test-only factory for package-private SaveSession construction seams. */
public final class SaveSessionTestFactory {
    private SaveSessionTestFactory() {
    }

    public static SaveSession open(
            Path savePath,
            WorldMetadata metadata
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(metadata, "metadata is required");
        return new SaveSession(
                savePath,
                new SqliteSaveConnection().openReadOnly(savePath),
                new SaveSnapshot(metadata, Map.of())
        );
    }
}
