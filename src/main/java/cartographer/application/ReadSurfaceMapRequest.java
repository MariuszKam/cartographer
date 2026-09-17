package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;

public record ReadSurfaceMapRequest(
        Path savePath,
        WorldPosition center,
        int radius,
        boolean ignoreFoliage,
        boolean requireLiquidLayer
) {
    public ReadSurfaceMapRequest {
        Objects.requireNonNull(savePath, "save path is required");
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) throw new IllegalArgumentException("radius must be positive");
    }
}
