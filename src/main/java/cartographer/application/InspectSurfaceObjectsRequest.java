package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record InspectSurfaceObjectsRequest(
        Path savePath,
        String resourceKey,
        int radius,
        Optional<WorldPosition> center
) {
    public InspectSurfaceObjectsRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("resource key is required");
        }
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}
