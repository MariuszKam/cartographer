package cartographer.cache;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;
import cartographer.scanner.CachedSurfaceTileView;
import cartographer.scanner.SurfaceTile;

import java.util.Arrays;
import java.util.Objects;

/** Immutable full-mapchunk Surface data, independent of request geometry. */
public final class SurfaceCacheTile implements CachedSurfaceTileView {
    public static final byte CONSIDERED = 1 << 1;
    public static final byte RESOLVED = 1 << 2;
    public static final byte LIQUID_UNAVAILABLE = 1 << 3;
    public static final byte LEGAL_STATE_MASK = CONSIDERED | RESOLVED | LIQUID_UNAVAILABLE;

    public enum SourceMode {
        RAIN_HEIGHT_FAST(1),
        FALLBACK(2);

        private final int code;

        SourceMode(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        static SourceMode decode(int code) {
            return switch (code) {
                case 1 -> RAIN_HEIGHT_FAST;
                case 2 -> FALLBACK;
                default -> throw new IllegalArgumentException("invalid Surface source mode: " + code);
            };
        }
    }

    private final MapChunkCoordinate coordinate;
    private final int worldSizeX;
    private final int worldSizeZ;
    private final int width;
    private final int height;
    private final byte[] state;
    private final int[] surfaceY;
    private final int[] blockIds;
    private final int[] liquidBlockIds;
    private final byte[] surfaceClassCodes;
    private final SourceMode sourceMode;
    private final int diagnosticColumnsScanned;
    private final int diagnosticEmptyColumns;
    private final int diagnosticLiquidUnavailableColumns;

    public SurfaceCacheTile(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeZ,
            byte[] state,
            int[] surfaceY,
            int[] blockIds,
            int[] liquidBlockIds,
            byte[] surfaceClassCodes,
            SourceMode sourceMode,
            int diagnosticColumnsScanned,
            int diagnosticEmptyColumns,
            int diagnosticLiquidUnavailableColumns
    ) {
        this(
                coordinate,
                worldSizeX,
                worldSizeZ,
                state,
                surfaceY,
                blockIds,
                liquidBlockIds,
                surfaceClassCodes,
                sourceMode,
                diagnosticColumnsScanned,
                diagnosticEmptyColumns,
                diagnosticLiquidUnavailableColumns,
                true
        );
    }

    private SurfaceCacheTile(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeZ,
            byte[] state,
            int[] surfaceY,
            int[] blockIds,
            int[] liquidBlockIds,
            byte[] surfaceClassCodes,
            SourceMode sourceMode,
            int diagnosticColumnsScanned,
            int diagnosticEmptyColumns,
            int diagnosticLiquidUnavailableColumns,
            boolean copyArrays
    ) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate is required");
        if (worldSizeX <= 0 || worldSizeZ <= 0) {
            throw new IllegalArgumentException("world dimensions must be positive");
        }
        Geometry geometry = deriveGeometry(coordinate, worldSizeX, worldSizeZ);
        int width = geometry.width();
        int height = geometry.height();
        int cells = Math.multiplyExact(width, height);
        this.worldSizeX = worldSizeX;
        this.worldSizeZ = worldSizeZ;
        this.width = width;
        this.height = height;
        this.state = prepare(state, cells, "state", copyArrays);
        this.surfaceY = prepare(surfaceY, cells, "surface Y", copyArrays);
        this.blockIds = prepare(blockIds, cells, "block IDs", copyArrays);
        this.liquidBlockIds = prepare(
                liquidBlockIds,
                cells,
                "liquid block IDs",
                copyArrays
        );
        this.surfaceClassCodes = prepare(
                surfaceClassCodes,
                cells,
                "surface class codes",
                copyArrays
        );
        this.sourceMode = Objects.requireNonNull(sourceMode, "source mode is required");
        if (diagnosticColumnsScanned < 0
                || diagnosticEmptyColumns < 0
                || diagnosticLiquidUnavailableColumns < 0
                || diagnosticEmptyColumns > diagnosticColumnsScanned
                || diagnosticLiquidUnavailableColumns > diagnosticColumnsScanned) {
            throw new IllegalArgumentException("invalid Surface diagnostic counters");
        }
        this.diagnosticColumnsScanned = diagnosticColumnsScanned;
        this.diagnosticEmptyColumns = diagnosticEmptyColumns;
        this.diagnosticLiquidUnavailableColumns = diagnosticLiquidUnavailableColumns;
        validateCells();
    }

    @Override
    public MapChunkCoordinate coordinate() { return coordinate; }
    public int worldSizeX() { return worldSizeX; }
    public int worldSizeZ() { return worldSizeZ; }
    @Override
    public int width() { return width; }
    @Override
    public int height() { return height; }
    public int cellCount() { return state.length; }
    public SourceMode sourceMode() { return sourceMode; }
    @Override
    public boolean fallbackMode() { return sourceMode == SourceMode.FALLBACK; }
    @Override
    public int diagnosticColumnsScanned() { return diagnosticColumnsScanned; }
    @Override
    public int diagnosticEmptyColumns() { return diagnosticEmptyColumns; }
    @Override
    public int diagnosticLiquidUnavailableColumns() {
        return diagnosticLiquidUnavailableColumns;
    }

    @Override
    public byte stateAtIndex(int cellIndex) {
        return state[Objects.checkIndex(cellIndex, state.length)];
    }

    @Override
    public int surfaceYAtIndex(int cellIndex) {
        return surfaceY[Objects.checkIndex(cellIndex, surfaceY.length)];
    }

    @Override
    public int blockIdAtIndex(int cellIndex) {
        return blockIds[Objects.checkIndex(cellIndex, blockIds.length)];
    }

    @Override
    public int liquidBlockIdAtIndex(int cellIndex) {
        return liquidBlockIds[
                Objects.checkIndex(cellIndex, liquidBlockIds.length)
        ];
    }

    @Override
    public byte surfaceClassCodeAtIndex(int cellIndex) {
        return surfaceClassCodes[
                Objects.checkIndex(cellIndex, surfaceClassCodes.length)
        ];
    }

    byte[] stateView() { return state; }
    int[] surfaceYView() { return surfaceY; }
    int[] blockIdsView() { return blockIds; }
    int[] liquidBlockIdsView() { return liquidBlockIds; }
    byte[] surfaceClassCodesView() { return surfaceClassCodes; }

    public boolean matchesWorld(WorldMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata is required");
        return worldSizeX == metadata.mapSizeX() && worldSizeZ == metadata.mapSizeZ();
    }

    /**
     * Converts a request tile only after proving it contains the complete
     * world-valid mapchunk domain. Request-clipped tiles are rejected.
     */
    public static SurfaceCacheTile fromComplete(
            SurfaceTile tile,
            WorldMetadata metadata,
            SourceMode sourceMode,
            int diagnosticColumnsScanned,
            int diagnosticEmptyColumns,
            int diagnosticLiquidUnavailableColumns
    ) {
        Objects.requireNonNull(tile, "Surface tile is required");
        Objects.requireNonNull(metadata, "metadata is required");
        MapChunkCoordinate coordinate = new MapChunkCoordinate(tile.tileX(), tile.tileZ());
        Geometry geometry = deriveGeometry(coordinate, metadata.mapSizeX(), metadata.mapSizeZ());
        if (tile.width() != geometry.width() || tile.height() != geometry.height()) {
            throw new IllegalArgumentException("Surface tile dimensions do not cover the full mapchunk");
        }
        int cells = geometry.cellCount();
        byte[] state = new byte[cells];
        int[] surfaceY = new int[cells];
        int[] blockIds = new int[cells];
        int[] liquidIds = new int[cells];
        byte[] classes = new byte[cells];
        byte unknown = SurfaceClassCode.encode(SurfaceClass.UNKNOWN);
        Arrays.fill(classes, unknown);
        for (int localZ = 0; localZ < geometry.height(); localZ++) {
            for (int localX = 0; localX < geometry.width(); localX++) {
                if (!tile.isActive(localX, localZ)) {
                    throw new IllegalArgumentException("request-clipped Surface tile cannot be cached");
                }
                int index = localZ * geometry.width() + localX;
                byte cellState = 0;
                if (tile.isConsidered(localX, localZ)) cellState |= CONSIDERED;
                if (tile.isResolved(localX, localZ)) {
                    cellState |= RESOLVED;
                    surfaceY[index] = tile.surfaceYAt(localX, localZ);
                    blockIds[index] = tile.blockIdAt(localX, localZ);
                    liquidIds[index] = tile.liquidBlockIdAt(localX, localZ);
                    classes[index] = SurfaceClassCode.encode(tile.surfaceClassAt(localX, localZ));
                }
                if (tile.isLiquidUnavailable(localX, localZ)) cellState |= LIQUID_UNAVAILABLE;
                state[index] = cellState;
            }
        }
        return owned(
                coordinate,
                metadata.mapSizeX(),
                metadata.mapSizeZ(),
                state,
                surfaceY,
                blockIds,
                liquidIds,
                classes,
                sourceMode,
                diagnosticColumnsScanned,
                diagnosticEmptyColumns,
                diagnosticLiquidUnavailableColumns
        );
    }

    static SurfaceCacheTile owned(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeZ,
            byte[] state,
            int[] surfaceY,
            int[] blockIds,
            int[] liquidBlockIds,
            byte[] surfaceClassCodes,
            SourceMode sourceMode,
            int diagnosticColumnsScanned,
            int diagnosticEmptyColumns,
            int diagnosticLiquidUnavailableColumns
    ) {
        return new SurfaceCacheTile(
                coordinate,
                worldSizeX,
                worldSizeZ,
                state,
                surfaceY,
                blockIds,
                liquidBlockIds,
                surfaceClassCodes,
                sourceMode,
                diagnosticColumnsScanned,
                diagnosticEmptyColumns,
                diagnosticLiquidUnavailableColumns,
                false
        );
    }

    private void validateCells() {
        byte unknown = SurfaceClassCode.encode(SurfaceClass.UNKNOWN);
        for (int index = 0; index < state.length; index++) {
            byte cellState = state[index];
            if ((cellState & ~LEGAL_STATE_MASK) != 0) {
                throw new IllegalArgumentException("Surface cache tile contains illegal state bits");
            }
            boolean resolved = (cellState & RESOLVED) != 0;
            if (resolved && (cellState & CONSIDERED) == 0) {
                throw new IllegalArgumentException("resolved Surface cell must be considered");
            }
            if ((cellState & LIQUID_UNAVAILABLE) != 0
                    && (cellState & CONSIDERED) == 0) {
                throw new IllegalArgumentException("liquid-unavailable Surface cell must be considered");
            }
            if (!resolved && (surfaceY[index] != 0 || blockIds[index] != 0
                    || liquidBlockIds[index] != 0 || surfaceClassCodes[index] != unknown)) {
                throw new IllegalArgumentException("unresolved Surface cell is not canonical");
            }
            SurfaceClassCode.decode(surfaceClassCodes[index]);
        }
    }

    private int index(int localX, int localZ) {
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= height) {
            throw new IndexOutOfBoundsException("local cell is outside Surface tile");
        }
        return localZ * width + localX;
    }

    private static byte[] prepare(
            byte[] values,
            int expected,
            String name,
            boolean copy
    ) {
        Objects.requireNonNull(values, name + " is required");
        if (values.length != expected) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return copy ? Arrays.copyOf(values, values.length) : values;
    }

    private static int[] prepare(
            int[] values,
            int expected,
            String name,
            boolean copy
    ) {
        Objects.requireNonNull(values, name + " is required");
        if (values.length != expected) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return copy ? Arrays.copyOf(values, values.length) : values;
    }

    static Geometry deriveGeometry(MapChunkCoordinate coordinate, int worldSizeX, int worldSizeZ) {
        long tileStartX = checkedTileStart(coordinate.x(), "X");
        long tileStartZ = checkedTileStart(coordinate.z(), "Z");
        if (tileStartX < 0 || tileStartX >= worldSizeX
                || tileStartZ < 0 || tileStartZ >= worldSizeZ) {
            throw new IllegalArgumentException("mapchunk coordinate is outside world bounds");
        }
        return new Geometry(
                derivedDimension(worldSizeX, tileStartX),
                derivedDimension(worldSizeZ, tileStartZ)
        );
    }

    private static long checkedTileStart(int coordinate, String axis) {
        try {
            return Math.multiplyExact(coordinate, (long) MapChunk.SIZE);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("mapchunk " + axis + " coordinate overflows", exception);
        }
    }

    private static int derivedDimension(int worldSize, long tileStart) {
        long remaining = worldSize - tileStart;
        if (remaining <= 0 || remaining > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("derived Surface tile dimension is invalid");
        }
        return (int) Math.min(MapChunk.SIZE, remaining);
    }

    record Geometry(int width, int height) {
        int cellCount() {
            return Math.multiplyExact(width, height);
        }
    }
}
