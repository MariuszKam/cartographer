package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record InspectSurfaceObjectsRequest(
        Path savePath,
        SurfaceResourceMatch match,
        int radius,
        Optional<WorldPosition> center
) {
    public InspectSurfaceObjectsRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}
