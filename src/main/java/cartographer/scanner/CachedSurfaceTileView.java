package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;

/**
 * Read-only primitive view of one validated cached Surface mapchunk.
 *
 * <p>The interface exposes cells rather than backing arrays so snapshot
 * consumers can reuse immutable cache data without defensive-array clones.</p>
 */
public interface CachedSurfaceTileView {
    MapChunkCoordinate coordinate();

    int width();

    int height();

    byte stateAt(int localX, int localZ);

    int surfaceYAt(int localX, int localZ);

    int blockIdAt(int localX, int localZ);

    int liquidBlockIdAt(int localX, int localZ);

    byte surfaceClassCodeAt(int localX, int localZ);

    boolean fallbackMode();

    int diagnosticColumnsScanned();

    int diagnosticEmptyColumns();

    int diagnosticLiquidUnavailableColumns();
}
