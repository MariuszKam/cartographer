package cartographer.application;

import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderedMapTile;

/**
 * Split pipeline used by ProgressiveMapSession.
 *
 * <p>{@link #load(RenderTileCoordinate)} is called only by the session's
 * serialized source-I/O thread. {@link #render(RenderTileCoordinate, MapTileData)}
 * runs on bounded CPU render workers and must not use SaveSession/JDBC state.</p>
 */
public interface ProgressiveTilePipeline {
    MapTileData load(RenderTileCoordinate coordinate);

    RenderedMapTile render(
            RenderTileCoordinate coordinate,
            MapTileData data
    );
}
