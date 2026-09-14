package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record DiscoverObservedSurfaceResourcesRequest(
        Path savePath,
        int radius,
        Optional<WorldPosition> center
) {
    public DiscoverObservedSurfaceResourcesRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}
