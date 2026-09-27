package cartographer.application;

import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import cartographer.render.RenderedMapTile;

/**
 * Split pipeline used by ProgressiveMapSession.
 *
 * <p>{@link #load(RenderTileCoordinate)} and
 * {@link #discoverNextBatch()} are called only by the session's serialized
 * source-I/O thread. {@link #render(RenderTileCoordinate, MapTileData)} runs
 * on bounded CPU render workers and must not use SaveSession/JDBC state.</p>
 */
public interface ProgressiveTilePipeline extends AutoCloseable {
    MapTileData load(RenderTileCoordinate coordinate);

    RenderedMapTile render(
            RenderTileCoordinate coordinate,
            MapTileData data
    );

    default RenderedMapTile render(
            RenderTileKey key,
            MapTileData data
    ) {
        return render(key.coordinate(), data);
    }

    default ProgressiveDiscoveryBatch discoverNextBatch() {
        return ProgressiveDiscoveryBatch.completed();
    }

    @Override
    default void close() {
        // Most test/in-memory pipelines own no source resource.
    }
}
