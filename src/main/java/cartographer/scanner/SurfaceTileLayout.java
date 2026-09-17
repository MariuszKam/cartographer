package cartographer.scanner;

import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.WorldMetadata;

import java.util.Objects;

/**
 * Checked geometry for the compact Surface tile model.
 *
 * <p>Tiles use the mapchunk height-map domain. Server-chunk arithmetic is
 * deliberately kept out of this class and must use
 * {@link ChunkCoordinate#SIZE_BLOCKS} at its call site.</p>
 */
public final class SurfaceTileLayout {
    private final int centerWorldX;
    private final int centerWorldZ;
    private final int radius;
    private final long radiusSquared;
    private final int worldSizeX;
    private final int worldSizeZ;
    private final int firstTileX;
    private final int lastTileX;
    private final int firstTileZ;
    private final int lastTileZ;
    private final int tileWidthCount;
    private final int tileHeightCount;
    private final int tileCount;
    private final long cellCount;

    private SurfaceTileLayout(
            int centerWorldX,
            int centerWorldZ,
            int radius,
            int worldSizeX,
            int worldSizeZ
    ) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (worldSizeX <= 0 || worldSizeZ <= 0) {
            throw new IllegalArgumentException("world dimensions must be positive");
        }

        this.centerWorldX = centerWorldX;
        this.centerWorldZ = centerWorldZ;
        this.radius = radius;
        this.radiusSquared = checkedMultiply(radius, radius, "radius squared");
        this.worldSizeX = worldSizeX;
        this.worldSizeZ = worldSizeZ;

        long minWorldX = Math.max(0L, checkedSubtract(centerWorldX, radius, "minimum world X"));
        long maxWorldX = Math.min(
                (long) worldSizeX - 1L,
                checkedAdd(centerWorldX, radius, "maximum world X")
        );
        long minWorldZ = Math.max(0L, checkedSubtract(centerWorldZ, radius, "minimum world Z"));
        long maxWorldZ = Math.min(
                (long) worldSizeZ - 1L,
                checkedAdd(centerWorldZ, radius, "maximum world Z")
        );

        if (minWorldX > maxWorldX || minWorldZ > maxWorldZ) {
            firstTileX = 0;
            lastTileX = -1;
            firstTileZ = 0;
            lastTileZ = -1;
            tileWidthCount = 0;
            tileHeightCount = 0;
            tileCount = 0;
            cellCount = 0L;
            return;
        }

        firstTileX = checkedTileCoordinate(Math.floorDiv(minWorldX, MapChunk.SIZE), "first tile X");
        lastTileX = checkedTileCoordinate(Math.floorDiv(maxWorldX, MapChunk.SIZE), "last tile X");
        firstTileZ = checkedTileCoordinate(Math.floorDiv(minWorldZ, MapChunk.SIZE), "first tile Z");
        lastTileZ = checkedTileCoordinate(Math.floorDiv(maxWorldZ, MapChunk.SIZE), "last tile Z");
        tileWidthCount = checkedRangeSize(firstTileX, lastTileX, "tile width");
        tileHeightCount = checkedRangeSize(firstTileZ, lastTileZ, "tile height");
        tileCount = checkedInt(
                checkedMultiply(tileWidthCount, tileHeightCount, "tile count"),
                "tile count"
        );

        long totalCells = 0L;
        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
            totalCells = checkedAdd(
                    totalCells,
                    tileCellCount(tileXAt(tileIndex), tileZAt(tileIndex)),
                    "cell count"
            );
        }
        cellCount = totalCells;
    }

    public static SurfaceTileLayout forSurface(
            double centerX,
            double centerZ,
            int radius,
            WorldMetadata metadata
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        long roundedX = Math.round(centerX);
        long roundedZ = Math.round(centerZ);
        int checkedX = checkedInt(roundedX, "rounded center X");
        int checkedZ = checkedInt(roundedZ, "rounded center Z");
        return new SurfaceTileLayout(
                checkedX,
                checkedZ,
                radius,
                metadata.mapSizeX(),
                metadata.mapSizeZ()
        );
    }

    public static SurfaceTileLayout forSurface(
            int centerX,
            int centerZ,
            int radius,
            WorldMetadata metadata
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        return new SurfaceTileLayout(
                centerX,
                centerZ,
                radius,
                metadata.mapSizeX(),
                metadata.mapSizeZ()
        );
    }

    public int centerWorldX() {
        return centerWorldX;
    }

    public int centerWorldZ() {
        return centerWorldZ;
    }

    public int radius() {
        return radius;
    }

    public long radiusSquared() {
        return radiusSquared;
    }

    public int worldSizeX() {
        return worldSizeX;
    }

    public int worldSizeZ() {
        return worldSizeZ;
    }

    public int tileWidthCount() {
        return tileWidthCount;
    }

    public int tileHeightCount() {
        return tileHeightCount;
    }

    public int tileCount() {
        return tileCount;
    }

    public long cellCount() {
        return cellCount;
    }

    public int firstTileX() {
        return firstTileX;
    }

    public int lastTileX() {
        return lastTileX;
    }

    public int firstTileZ() {
        return firstTileZ;
    }

    public int lastTileZ() {
        return lastTileZ;
    }

    /** Returns true when the coordinate is in world bounds and the circle. */
    public boolean isActive(int worldX, int worldZ) {
        return contains(worldX, worldZ) && withinCircle(worldX, worldZ);
    }

    /** Returns true when the coordinate is in the world bounds. */
    public boolean contains(int worldX, int worldZ) {
        return worldX >= 0
                && worldX < worldSizeX
                && worldZ >= 0
                && worldZ < worldSizeZ;
    }

    public boolean withinCircle(int worldX, int worldZ) {
        long dx = (long) worldX - centerWorldX;
        long dz = (long) worldZ - centerWorldZ;
        return checkedAdd(
                checkedMultiply(dx, dx, "circle X distance squared"),
                checkedMultiply(dz, dz, "circle Z distance squared"),
                "circle distance squared"
        ) <= radiusSquared;
    }

    /** Uses floor division in the mapchunk tile domain, including negatives. */
    public int tileXForWorld(int worldX) {
        return checkedTileCoordinate(
                Math.floorDiv((long) worldX, MapChunk.SIZE),
                "tile X"
        );
    }

    /** Uses floor division in the mapchunk tile domain, including negatives. */
    public int tileZForWorld(int worldZ) {
        return checkedTileCoordinate(
                Math.floorDiv((long) worldZ, MapChunk.SIZE),
                "tile Z"
        );
    }

    /** Uses floor modulus in the mapchunk tile domain, including negatives. */
    public int localXForWorld(int worldX) {
        return Math.floorMod(worldX, MapChunk.SIZE);
    }

    /** Uses floor modulus in the mapchunk tile domain, including negatives. */
    public int localZForWorld(int worldZ) {
        return Math.floorMod(worldZ, MapChunk.SIZE);
    }

    public int worldXForTileLocal(int tileX, int localX) {
        validateLocal(localX, "local X");
        return checkedInt(
                checkedAdd(
                        checkedMultiply(tileX, MapChunk.SIZE, "world X tile product"),
                        localX,
                        "world X"
                ),
                "world X"
        );
    }

    public int worldZForTileLocal(int tileZ, int localZ) {
        validateLocal(localZ, "local Z");
        return checkedInt(
                checkedAdd(
                        checkedMultiply(tileZ, MapChunk.SIZE, "world Z tile product"),
                        localZ,
                        "world Z"
                ),
                "world Z"
        );
    }

    /** Tile order is Z ascending, then X ascending. */
    public int tileIndex(int tileX, int tileZ) {
        if (tileX < firstTileX || tileX > lastTileX
                || tileZ < firstTileZ || tileZ > lastTileZ) {
            throw new IndexOutOfBoundsException("tile is outside layout");
        }
        int row = tileZ - firstTileZ;
        int column = tileX - firstTileX;
        return checkedInt(
                checkedAdd(
                        checkedMultiply(row, tileWidthCount, "tile row index"),
                        column,
                        "tile index"
                ),
                "tile index"
        );
    }

    public int tileXAt(int tileIndex) {
        validateTileIndex(tileIndex);
        return firstTileX + tileIndex % tileWidthCount;
    }

    public int tileZAt(int tileIndex) {
        validateTileIndex(tileIndex);
        return firstTileZ + tileIndex / tileWidthCount;
    }

    public int tileWidth(int tileX) {
        validateTileX(tileX);
        int start = checkedInt(
                checkedMultiply(tileX, MapChunk.SIZE, "tile X world product"),
                "tile X world product"
        );
        return Math.min(MapChunk.SIZE, worldSizeX - start);
    }

    public int tileHeight(int tileZ) {
        validateTileZ(tileZ);
        int start = checkedInt(
                checkedMultiply(tileZ, MapChunk.SIZE, "tile Z world product"),
                "tile Z world product"
        );
        return Math.min(MapChunk.SIZE, worldSizeZ - start);
    }

    public int tileCellCount(int tileX, int tileZ) {
        return checkedInt(
                checkedMultiply(tileWidth(tileX), tileHeight(tileZ), "tile cell count"),
                "tile cell count"
        );
    }

    public int cellIndex(int tileX, int tileZ, int localX, int localZ) {
        validateLocal(localX, "local X");
        validateLocal(localZ, "local Z");
        int width = tileWidth(tileX);
        int height = tileHeight(tileZ);
        if (localX >= width || localZ >= height) {
            throw new IndexOutOfBoundsException("local cell is outside edge tile");
        }
        return checkedInt(
                checkedAdd(
                        checkedMultiply(localZ, width, "cell row index"),
                        localX,
                        "cell index"
                ),
                "cell index"
        );
    }

    private void validateTileIndex(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= tileCount) {
            throw new IndexOutOfBoundsException("tile index is outside layout");
        }
    }

    private void validateTileX(int tileX) {
        if (tileX < firstTileX || tileX > lastTileX) {
            throw new IndexOutOfBoundsException("tile X is outside layout");
        }
    }

    private void validateTileZ(int tileZ) {
        if (tileZ < firstTileZ || tileZ > lastTileZ) {
            throw new IndexOutOfBoundsException("tile Z is outside layout");
        }
    }

    private void validateLocal(int local, String name) {
        if (local < 0 || local >= MapChunk.SIZE) {
            throw new IndexOutOfBoundsException(name + " is outside mapchunk tile");
        }
    }

    private static int checkedRangeSize(int first, int last, String name) {
        return checkedInt(
                checkedAdd((long) last - first, 1L, name),
                name
        );
    }

    private static int checkedTileCoordinate(long value, String name) {
        return checkedInt(value, name);
    }

    private static int checkedInt(long value, String name) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds int range: " + value);
        }
        return (int) value;
    }

    private static long checkedAdd(long left, long right, String name) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " overflows", exception);
        }
    }

    private static long checkedSubtract(long left, long right, String name) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " overflows", exception);
        }
    }

    private static long checkedMultiply(long left, long right, String name) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " overflows", exception);
        }
    }
}
