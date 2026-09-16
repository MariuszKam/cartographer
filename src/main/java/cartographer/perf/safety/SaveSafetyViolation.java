package cartographer.perf.safety;

import java.nio.file.Path;
import java.util.Objects;

/** One deterministic protected-file invariant violation. */
public record SaveSafetyViolation(SaveSafetyViolationType type, Path path) {
    public SaveSafetyViolation {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(path, "path is required");
        path = path.toAbsolutePath().normalize();
    }
}
