package cartographer.scanner;

import cartographer.model.SurfaceClass;

import java.util.Objects;

/** Immutable compact Surface result; no bulk SurfaceBlock representation. */
public final class SurfaceMap {
    private final SurfaceTileLayout layout;
    private final SurfaceTile[] tiles;

    SurfaceMap(SurfaceTileLayout layout, SurfaceTile[] tiles) {
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.tiles = Objects.requireNonNull(tiles, "tiles are required").clone();
        if (this.tiles.length != layout.tileCount()) {
            throw new IllegalArgumentException("tile count does not match layout");
        }
    }

    public SurfaceTileLayout layout() {
        return layout;
    }

    public int tileCount() {
        return tiles.length;
    }

    public SurfaceTile tileAt(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= tiles.length) {
            throw new IndexOutOfBoundsException("tile index is outside map");
        }
        return tiles[tileIndex];
    }

    public boolean contains(int worldX, int worldZ) {
        return layout.isActive(worldX, worldZ);
    }

    public boolean isConsidered(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            return false;
        }
        return cell(worldX, worldZ).isConsidered(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public boolean isResolved(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            return false;
        }
        return cell(worldX, worldZ).isResolved(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public boolean isLiquidUnavailable(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            return false;
        }
        return cell(worldX, worldZ).isLiquidUnavailable(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public int surfaceYAt(int worldX, int worldZ) {
        return resolvedCell(worldX, worldZ).surfaceYAt(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public int blockIdAt(int worldX, int worldZ) {
        return resolvedCell(worldX, worldZ).blockIdAt(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public int liquidBlockIdAt(int worldX, int worldZ) {
        return resolvedCell(worldX, worldZ).liquidBlockIdAt(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    public SurfaceClass surfaceClassAt(int worldX, int worldZ) {
        return resolvedCell(worldX, worldZ).surfaceClassAt(
                layout.localXForWorld(worldX), layout.localZForWorld(worldZ));
    }

    /** Deterministic tile-Z/tile-X then local-Z/local-X primitive traversal. */
    public void forEachCell(CellConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
            SurfaceTile tile = tiles[tileIndex];
            for (int localZ = 0; localZ < tile.height(); localZ++) {
                for (int localX = 0; localX < tile.width(); localX++) {
                    int worldX = layout.worldXForTileLocal(tile.tileX(), localX);
                    int worldZ = layout.worldZForTileLocal(tile.tileZ(), localZ);
                    int index = localZ * tile.width() + localX;
                    consumer.accept(
                            worldX,
                            worldZ,
                            tile.stateAt(index),
                            tile.surfaceYAtIndex(index),
                            tile.blockIdAtIndex(index),
                            tile.liquidBlockIdAtIndex(index),
                            tile.surfaceClassAtIndex(index)
                    );
                }
            }
        }
    }

    /** Deterministic primitive traversal of resolved active cells only. */
    public void forEachResolvedCell(ResolvedCellConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        forEachCell((worldX, worldZ, state, surfaceY, blockId, liquidBlockId, surfaceClass) -> {
            if ((state & SurfaceTile.RESOLVED) != 0) {
                consumer.accept(
                        worldX, worldZ, surfaceY, blockId, liquidBlockId, surfaceClass);
            }
        });
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(
                int worldX,
                int worldZ,
                byte state,
                int surfaceY,
                int blockId,
                int liquidBlockId,
                SurfaceClass surfaceClass
        );
    }

    @FunctionalInterface
    public interface ResolvedCellConsumer {
        void accept(
                int worldX,
                int worldZ,
                int surfaceY,
                int blockId,
                int liquidBlockId,
                SurfaceClass surfaceClass
        );
    }

    private SurfaceTile resolvedCell(int worldX, int worldZ) {
        SurfaceTile tile = cell(worldX, worldZ);
        int localX = layout.localXForWorld(worldX);
        int localZ = layout.localZForWorld(worldZ);
        if (!tile.isResolved(localX, localZ)) {
            throw new IllegalStateException("surface cell is not resolved");
        }
        return tile;
    }

    private SurfaceTile cell(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            throw new IndexOutOfBoundsException("world coordinate is outside Surface map");
        }
        int tileX = layout.tileXForWorld(worldX);
        int tileZ = layout.tileZForWorld(worldZ);
        return tiles[layout.tileIndex(tileX, tileZ)];
    }
}
