package cartographer.marker;

import cartographer.model.DisplayPosition;

import java.util.Objects;

/** User marker persisted in Vintage Story display-coordinate space. */
public record UserMarker(
        String name,
        DisplayPosition position
) {

    public UserMarker {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Marker name is required"
            );
        }
        name = name.trim();
        position = Objects.requireNonNull(
                position,
                "Marker display position is required"
        );
        if (!Double.isFinite(position.x())
                || !Double.isFinite(position.y())
                || !Double.isFinite(position.z())) {
            throw new IllegalArgumentException(
                    "Marker display coordinates must be finite"
            );
        }
        if (position.y() != 0.0) {
            throw new IllegalArgumentException(
                    "Marker display Y must be zero"
            );
        }
    }
}
