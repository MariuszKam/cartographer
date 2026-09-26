package cartographer.application;

import cartographer.marker.UserMarker;
import cartographer.model.HomeState;

import java.util.List;
import java.util.Objects;

/** Bounded non-save decorations retained for local Workstation recomposition. */
public record MapDecorationState(
        HomeState home,
        List<UserMarker> userMarkers,
        boolean userMarkersAvailable
) {
    public MapDecorationState {
        Objects.requireNonNull(home, "home is required");
        userMarkers = List.copyOf(
                Objects.requireNonNull(userMarkers, "user markers are required")
        );
    }
}
