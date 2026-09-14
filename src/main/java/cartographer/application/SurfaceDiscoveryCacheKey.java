package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;

/** Exact in-memory identity of a discovery context. */
public record SurfaceDiscoveryCacheKey(Path savePath, int radius, double centerX, double centerZ) {
    public SurfaceDiscoveryCacheKey {
        Objects.requireNonNull(savePath, "savePath is required");
        savePath = savePath.toAbsolutePath().normalize();
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("center coordinates must be finite");
        }
    }

    public static SurfaceDiscoveryCacheKey of(Path savePath, int radius, WorldPosition center) {
        Objects.requireNonNull(center, "center is required");
        return new SurfaceDiscoveryCacheKey(savePath, radius, center.x(), center.z());
    }
}
