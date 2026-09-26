package cartographer.ui.workstation;

import cartographer.render.RenderTileCoordinate;

import java.util.List;
import java.util.Objects;

/** Visible and near-visible render-tile demand for one viewport state. */
public record WorldMapViewportDemand(
        List<RenderTileCoordinate> visible,
        List<RenderTileCoordinate> prefetch
) {
    public WorldMapViewportDemand {
        Objects.requireNonNull(visible, "visible is required");
        Objects.requireNonNull(prefetch, "prefetch is required");
        visible = List.copyOf(visible);
        prefetch = List.copyOf(prefetch);
    }
}
