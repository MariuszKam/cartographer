package cartographer.application;

import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderedMapTile;

import java.util.Objects;

/** Events emitted by one generation-scoped progressive map session. */
public sealed interface ProgressiveMapEvent
        permits ProgressiveMapEvent.TileReady,
        ProgressiveMapEvent.TileFailed,
        ProgressiveMapEvent.DiscoveryComplete,
        ProgressiveMapEvent.SessionClosed {

    long generation();

    record TileReady(
            long generation,
            RenderedMapTile tile
    ) implements ProgressiveMapEvent {
        public TileReady {
            Objects.requireNonNull(tile, "tile is required");
        }
    }

    record TileFailed(
            long generation,
            RenderTileCoordinate coordinate,
            String detail
    ) implements ProgressiveMapEvent {
        public TileFailed {
            Objects.requireNonNull(coordinate, "coordinate is required");
            detail = detail == null || detail.isBlank()
                    ? "tile materialization failed"
                    : detail.trim();
        }
    }

    record DiscoveryComplete(
            long generation
    ) implements ProgressiveMapEvent {
    }

    record SessionClosed(
            long generation
    ) implements ProgressiveMapEvent {
    }
}
