package cartographer.perf;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;

import java.util.Arrays;
import java.util.Objects;

/** Immutable full-mapchunk Surface data, independent of request geometry. */
public final class SurfaceCacheTile {
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
        this.state = copyExact(state, cells, "state");
        this.surfaceY = copyExact(surfaceY, cells, "surface Y");
        this.blockIds = copyExact(blockIds, cells, "block IDs");
        this.liquidBlockIds = copyExact(liquidBlockIds, cells, "liquid block IDs");
        this.surfaceClassCodes = copyExact(surfaceClassCodes, cells, "surface class codes");
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

    public MapChunkCoordinate coordinate() { return coordinate; }
    public int worldSizeX() { return worldSizeX; }
    public int worldSizeZ() { return worldSizeZ; }
    public int width() { return width; }
    public int height() { return height; }
    public int cellCount() { return state.length; }
    public byte[] state() { return state.clone(); }
    public int[] surfaceY() { return surfaceY.clone(); }
    public int[] blockIds() { return blockIds.clone(); }
    public int[] liquidBlockIds() { return liquidBlockIds.clone(); }
    public byte[] surfaceClassCodes() { return surfaceClassCodes.clone(); }
    public SourceMode sourceMode() { return sourceMode; }
    public int diagnosticColumnsScanned() { return diagnosticColumnsScanned; }
    public int diagnosticEmptyColumns() { return diagnosticEmptyColumns; }
    public int diagnosticLiquidUnavailableColumns() { return diagnosticLiquidUnavailableColumns; }

    public boolean matchesWorld(WorldMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata is required");
        return worldSizeX == metadata.mapSizeX() && worldSizeZ == metadata.mapSizeZ();
    }

    public SurfaceClass surfaceClassAt(int localX, int localZ) {
        return SurfaceClassCode.decode(surfaceClassCodes[index(localX, localZ)]);
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

    private static byte[] copyExact(byte[] values, int expected, String name) {
        Objects.requireNonNull(values, name + " is required");
        if (values.length != expected) throw new IllegalArgumentException(name + " length is invalid");
        return Arrays.copyOf(values, values.length);
    }

    private static int[] copyExact(int[] values, int expected, String name) {
        Objects.requireNonNull(values, name + " is required");
        if (values.length != expected) throw new IllegalArgumentException(name + " length is invalid");
        return Arrays.copyOf(values, values.length);
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
            return Math.multiplyExact((long) coordinate, (long) MapChunk.SIZE);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("mapchunk " + axis + " coordinate overflows", exception);
        }
    }

    private static int derivedDimension(int worldSize, long tileStart) {
        long remaining = worldSize - tileStart;
        if (remaining <= 0 || remaining > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("derived Surface tile dimension is invalid");
        }
        return (int) Math.min((long) MapChunk.SIZE, remaining);
    }

    record Geometry(int width, int height) {
        int cellCount() {
            return Math.multiplyExact(width, height);
        }
    }
}
