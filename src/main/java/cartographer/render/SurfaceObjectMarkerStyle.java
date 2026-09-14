package cartographer.render;

import java.util.Objects;

public record SurfaceObjectMarkerStyle(
        SurfaceObjectMarkerShape shape,
        int radius
) {
    public SurfaceObjectMarkerStyle {
        Objects.requireNonNull(shape, "shape is required");
        if (radius < 1) {
            throw new IllegalArgumentException("marker radius must be positive");
        }
    }
}
