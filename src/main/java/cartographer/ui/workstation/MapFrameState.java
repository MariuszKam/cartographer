package cartographer.ui.workstation;

import java.util.Objects;
import java.util.Optional;

/** Single retained frame slot for the currently displayed Workstation map. */
public final class MapFrameState {
    private Optional<MapFrame> current = Optional.empty();

    public Optional<MapFrame> current() {
        return current;
    }

    public void retain(MapFrame frame) {
        current = Optional.of(Objects.requireNonNull(frame, "frame is required"));
    }

    public void clear() {
        current = Optional.empty();
    }
}
