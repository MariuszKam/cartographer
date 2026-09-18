package cartographer.scanner;

import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;

import java.util.Objects;

/** Immutable primitive-backed mapchunk-sized Surface tile. */
public final class SurfaceTile {
    /** Public primitive state bit: the cell is inside the requested circle. */
    public static final byte ACTIVE = 1;
    /** Public primitive state bit: the column was considered by scanning. */
    public static final byte CONSIDERED = 1 << 1;
    /** Public primitive state bit: a surface observation was resolved. */
    public static final byte RESOLVED = 1 << 2;
    /** Public primitive state bit: liquid data was unavailable. */
    public static final byte LIQUID_UNAVAILABLE = 1 << 3;

    private final int tileX;
    private final int tileZ;
    private final int width;
    private final int height;
    private final byte[] state;
    private final int[] surfaceY;
    private final int[] blockIds;
    private final int[] liquidBlockIds;
    private final byte[] surfaceClasses;

    SurfaceTile(
            int tileX,
            int tileZ,
            int width,
            int height,
            byte[] state,
            int[] surfaceY,
            int[] blockIds,
            int[] liquidBlockIds,
            byte[] surfaceClasses
    ) {
        this.tileX = tileX;
        this.tileZ = tileZ;
        this.width = width;
        this.height = height;
        this.state = Objects.requireNonNull(state, "state is required");
        this.surfaceY = Objects.requireNonNull(surfaceY, "surface Y is required");
        this.blockIds = Objects.requireNonNull(blockIds, "block IDs are required");
        this.liquidBlockIds = Objects.requireNonNull(liquidBlockIds, "liquid IDs are required");
        this.surfaceClasses = Objects.requireNonNull(surfaceClasses, "surface classes are required");
        int expected = Math.multiplyExact(width, height);
        if (width <= 0 || height <= 0
                || state.length != expected
                || surfaceY.length != expected
                || blockIds.length != expected
                || liquidBlockIds.length != expected
                || surfaceClasses.length != expected) {
            throw new IllegalArgumentException("tile arrays do not match tile dimensions");
        }
    }

    public int tileX() {
        return tileX;
    }

    public int tileZ() {
        return tileZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int cellCount() {
        return state.length;
    }

    public boolean isActive(int localX, int localZ) {
        return (state[index(localX, localZ)] & ACTIVE) != 0;
    }

    public boolean isConsidered(int localX, int localZ) {
        return (state[index(localX, localZ)] & CONSIDERED) != 0;
    }

    public boolean isResolved(int localX, int localZ) {
        return (state[index(localX, localZ)] & RESOLVED) != 0;
    }

    public boolean isLiquidUnavailable(int localX, int localZ) {
        return (state[index(localX, localZ)] & LIQUID_UNAVAILABLE) != 0;
    }

    public int surfaceYAt(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    public int blockIdAt(int localX, int localZ) {
        return blockIds[index(localX, localZ)];
    }

    public int liquidBlockIdAt(int localX, int localZ) {
        return liquidBlockIds[index(localX, localZ)];
    }

    public SurfaceClass surfaceClassAt(int localX, int localZ) {
        return decodeSurfaceClass(surfaceClasses[index(localX, localZ)]);
    }

    byte stateAt(int index) {
        return state[index];
    }

    int surfaceYAtIndex(int index) {
        return surfaceY[index];
    }

    int blockIdAtIndex(int index) {
        return blockIds[index];
    }

    int liquidBlockIdAtIndex(int index) {
        return liquidBlockIds[index];
    }

    SurfaceClass surfaceClassAtIndex(int index) {
        return decodeSurfaceClass(surfaceClasses[index]);
    }

    static byte encodeSurfaceClass(SurfaceClass value) {
        return SurfaceClassCode.encode(value);
    }

    private static SurfaceClass decodeSurfaceClass(byte code) {
        try {
            return SurfaceClassCode.decode(code);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("invalid surface class code: " + code, exception);
        }
    }

    private int index(int localX, int localZ) {
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= height) {
            throw new IndexOutOfBoundsException("local cell is outside tile");
        }
        return Math.addExact(Math.multiplyExact(localZ, width), localX);
    }
}
