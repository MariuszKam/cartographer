package cartographer.save;

import java.nio.file.Path;
import java.util.Objects;

/** Shared no-I/O normalization rule for save identity comparisons. */
public final class SavePathIdentity {
    private SavePathIdentity() {
    }

    public static Path normalize(Path savePath) {
        return Objects.requireNonNull(savePath, "save path is required")
                .toAbsolutePath()
                .normalize();
    }
}
