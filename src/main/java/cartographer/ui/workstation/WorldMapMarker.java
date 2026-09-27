package cartographer.ui.workstation;

import cartographer.model.WorldPosition;

import java.util.Objects;

/** Vector marker drawn independently of progressive raster tiles. */
public record WorldMapMarker(
        String label,
        WorldPosition position,
        Kind kind
) {
    public WorldMapMarker {
        label = label == null ? "" : label.trim();
        Objects.requireNonNull(position, "position is required");
        Objects.requireNonNull(kind, "kind is required");
    }

    public enum Kind {
        PLAYER,
        HOME,
        USER
    }
}
