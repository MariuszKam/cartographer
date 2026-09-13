package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ProspectingAreaRequest(
        Path savePath,
        Optional<WorldPosition> center,
        int radius,
        Optional<String> resource
) {
    public ProspectingAreaRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(resource, "resource is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (resource.isPresent() && resource.orElseThrow().isBlank()) {
            throw new IllegalArgumentException("resource must not be blank");
        }
    }
}
