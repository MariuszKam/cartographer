package cartographer.ui;

import cartographer.model.WorldPosition;

import java.util.Objects;

public record PlayerPositionSnapshot(
        WorldPosition absolute,
        PlayerPositionView display
) {
    public PlayerPositionSnapshot {
        Objects.requireNonNull(absolute, "absolute player position is required");
        Objects.requireNonNull(display, "display player position is required");
    }
}
