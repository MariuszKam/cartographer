package cartographer.scanner;

import cartographer.model.SurfaceClass;

import java.util.Arrays;
import java.util.Objects;

/**
 * Session-owned primitive Surface state. Finalization transfers tile-array
 * ownership to an immutable {@link SurfaceMap}; it does not copy cell data.
 */
public final class SurfaceTileAccumulator {
    private final SurfaceTileLayout layout;
    private TileState[] tiles;
    private boolean finished;

    public SurfaceTileAccumulator(SurfaceTileLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.tiles = new TileState[layout.tileCount()];
        for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
            int tileX = layout.tileXAt(tileIndex);
            int tileZ = layout.tileZAt(tileIndex);
            tiles[tileIndex] = new TileState(
                    layout.tileWidth(tileX),
                    layout.tileHeight(tileZ)
            );
            initializeActiveCells(tileIndex, tileX, tileZ);
        }
    }

    public SurfaceTileLayout layout() {
        return layout;
    }

    public void consider(int worldX, int worldZ) {
        ensureMutable();
        int cell = activeCell(worldX, worldZ);
        if (cell < 0) {
            return;
        }
        TileState tile = tiles[layout.tileIndex(
                layout.tileXForWorld(worldX),
                layout.tileZForWorld(worldZ)
        )];
        tile.state[cell] |= SurfaceTile.CONSIDERED;
    }

    public boolean markLiquidUnavailable(int worldX, int worldZ) {
        ensureMutable();
        int cell = activeCell(worldX, worldZ);
        if (cell < 0) {
            return false;
        }
        TileState tile = tileFor(worldX, worldZ);
        boolean wasSet = (tile.state[cell] & SurfaceTile.LIQUID_UNAVAILABLE) != 0;
        tile.state[cell] |= SurfaceTile.CONSIDERED | SurfaceTile.LIQUID_UNAVAILABLE;
        return !wasSet;
    }

    public void recordSurface(
            int worldX,
            int worldZ,
            int surfaceY,
            int blockId,
            int liquidBlockId,
            SurfaceClass surfaceClass
    ) {
        ensureMutable();
        Objects.requireNonNull(surfaceClass, "surface class is required");
        int cell = activeCell(worldX, worldZ);
        if (cell < 0) {
            return;
        }
        TileState tile = tileFor(worldX, worldZ);
        byte oldState = tile.state[cell];
        if ((oldState & SurfaceTile.RESOLVED) != 0
                && !isPreferred(
                surfaceY,
                blockId,
                liquidBlockId,
                surfaceClass,
                tile.surfaceY[cell],
                tile.blockIds[cell],
                tile.liquidBlockIds[cell],
                tile.surfaceClasses[cell]
        )) {
            tile.state[cell] |= SurfaceTile.CONSIDERED;
            return;
        }
        tile.state[cell] = (byte) (oldState | SurfaceTile.CONSIDERED | SurfaceTile.RESOLVED);
        tile.surfaceY[cell] = surfaceY;
        tile.blockIds[cell] = blockId;
        tile.liquidBlockIds[cell] = liquidBlockId;
        tile.surfaceClasses[cell] = SurfaceTile.encodeSurfaceClass(surfaceClass);
    }

    public SurfaceMap finish() {
        ensureMutable();
        SurfaceTile[] immutableTiles = new SurfaceTile[tiles.length];
        for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
            TileState tile = tiles[tileIndex];
            immutableTiles[tileIndex] = new SurfaceTile(
                    layout.tileXAt(tileIndex),
                    layout.tileZAt(tileIndex),
                    tile.width,
                    tile.height,
                    tile.state,
                    tile.surfaceY,
                    tile.blockIds,
                    tile.liquidBlockIds,
                    tile.surfaceClasses
            );
        }
        tiles = null;
        finished = true;
        return new SurfaceMap(layout, immutableTiles);
    }

    /** Resets all transient fast-path state before fallback owns this tile. */
    void resetForFallbackTile(int tileX, int tileZ) {
        ensureMutable();
        TileState tile = tiles[layout.tileIndex(tileX, tileZ)];
        for (int index = 0; index < tile.state.length; index++) {
            tile.state[index] &= SurfaceTile.ACTIVE;
        }
        Arrays.fill(tile.surfaceY, 0);
        Arrays.fill(tile.blockIds, 0);
        Arrays.fill(tile.liquidBlockIds, 0);
        Arrays.fill(tile.surfaceClasses, (byte) 0);
    }

    private void initializeActiveCells(int tileIndex, int tileX, int tileZ) {
        TileState tile = tiles[tileIndex];
        for (int localZ = 0; localZ < tile.height; localZ++) {
            for (int localX = 0; localX < tile.width; localX++) {
                int worldX = layout.worldXForTileLocal(tileX, localX);
                int worldZ = layout.worldZForTileLocal(tileZ, localZ);
                if (layout.isActive(worldX, worldZ)) {
                    tile.state[localZ * tile.width + localX] |= SurfaceTile.ACTIVE;
                }
            }
        }
    }

    private TileState tileFor(int worldX, int worldZ) {
        return tiles[layout.tileIndex(
                layout.tileXForWorld(worldX),
                layout.tileZForWorld(worldZ)
        )];
    }

    private int activeCell(int worldX, int worldZ) {
        if (!layout.isActive(worldX, worldZ)) {
            return -1;
        }
        int tileX = layout.tileXForWorld(worldX);
        int tileZ = layout.tileZForWorld(worldZ);
        return layout.cellIndex(
                tileX,
                tileZ,
                layout.localXForWorld(worldX),
                layout.localZForWorld(worldZ)
        );
    }

    private boolean isPreferred(
            int newY,
            int newBlockId,
            int newLiquidBlockId,
            SurfaceClass newClass,
            int oldY,
            int oldBlockId,
            int oldLiquidBlockId,
            byte oldClass
    ) {
        if (newY != oldY) {
            return newY > oldY;
        }
        int blockComparison = Integer.compare(newBlockId, oldBlockId);
        if (blockComparison != 0) {
            return blockComparison > 0;
        }
        int liquidComparison = Integer.compare(newLiquidBlockId, oldLiquidBlockId);
        if (liquidComparison != 0) {
            return liquidComparison > 0;
        }
        return Byte.compare(
                SurfaceTile.encodeSurfaceClass(newClass),
                oldClass
        ) > 0;
    }

    private void ensureMutable() {
        if (finished) {
            throw new IllegalStateException("Surface tile accumulator is finished");
        }
    }

    private static final class TileState {
        private final int width;
        private final int height;
        private final byte[] state;
        private final int[] surfaceY;
        private final int[] blockIds;
        private final int[] liquidBlockIds;
        private final byte[] surfaceClasses;

        private TileState(int width, int height) {
            this.width = width;
            this.height = height;
            int cells = Math.multiplyExact(width, height);
            this.state = new byte[cells];
            this.surfaceY = new int[cells];
            this.blockIds = new int[cells];
            this.liquidBlockIds = new int[cells];
            this.surfaceClasses = new byte[cells];
            byte unknown = SurfaceTile.encodeSurfaceClass(SurfaceClass.UNKNOWN);
            java.util.Arrays.fill(this.surfaceClasses, unknown);
        }
    }
}
