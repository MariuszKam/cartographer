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

    byte stateAtIndex(int cellIndex);

    int surfaceYAtIndex(int cellIndex);

    int blockIdAtIndex(int cellIndex);

    int liquidBlockIdAtIndex(int cellIndex);

    byte surfaceClassCodeAtIndex(int cellIndex);

    boolean fallbackMode();

    int diagnosticColumnsScanned();

    int diagnosticEmptyColumns();

    int diagnosticLiquidUnavailableColumns();
}
