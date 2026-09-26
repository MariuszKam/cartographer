package cartographer.geology.rock;

import cartographer.model.WorldPosition;

import java.util.Objects;

/** Immutable primitive row geometry for an inclusive integer circle. */
public final class RockCircleGeometry {
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int[] rowStartX;
    private final int[] rowLength;
    private final long[] rowOffset;
    private final long cellCount;

    RockCircleGeometry(int centerX, int centerZ, int radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        long rowCountLong = Math.addExact(
                Math.multiplyExact(radius, 2L),
                1L
        );
        if (rowCountLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rock circle has too many rows");
        }
        long minX = (long) centerX - radius;
        long maxX = (long) centerX + radius;
        long minZ = (long) centerZ - radius;
        long maxZ = (long) centerZ + radius;
        if (minX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE
                || minZ < Integer.MIN_VALUE || maxZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "rock circle exceeds supported world-coordinate range"
            );
        }

        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        int rowCount = Math.toIntExact(rowCountLong);
        long radiusSquared = Math.multiplyExact((long) radius, radius);
        long exactCellCount = preflightCellCount(radius, rowCount, radiusSquared);
        this.rowStartX = new int[rowCount];
        this.rowLength = new int[rowCount];
        this.rowOffset = new long[rowCount];
        long offset = 0;
        for (int row = 0; row < rowCount; row++) {
            long dz = (long) row - radius;
            long remaining = Math.subtractExact(radiusSquared, Math.multiplyExact(dz, dz));
            long halfWidth = floorSqrt(remaining);
            long startX = Math.subtractExact(centerX, halfWidth);
            long length = Math.addExact(Math.multiplyExact(halfWidth, 2L), 1L);
            if (startX < Integer.MIN_VALUE || startX > Integer.MAX_VALUE
                    || length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("rock circle row is too large");
            }
            rowStartX[row] = Math.toIntExact(startX);
            rowLength[row] = Math.toIntExact(length);
            rowOffset[row] = offset;
            offset = Math.addExact(offset, length);
        }
        if (offset != exactCellCount) {
            throw new IllegalStateException("circle geometry preflight mismatch");
        }
        this.cellCount = exactCellCount;
    }

    public static RockCircleGeometry from(WorldPosition center, int radius) {
        Objects.requireNonNull(center, "center is required");
        return new RockCircleGeometry(
                floorCoordinate(center.x()),
                floorCoordinate(center.z()),
                radius
        );
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int radius() {
        return radius;
    }

    public int rowCount() {
        return rowStartX.length;
    }

    public int rowStartX(int row) {
        checkRow(row);
        return rowStartX[row];
    }

    public int rowLength(int row) {
        checkRow(row);
        return rowLength[row];
    }

    public long rowOffset(int row) {
        checkRow(row);
        return rowOffset[row];
    }

    public int worldZForRow(int row) {
        checkRow(row);
        return Math.toIntExact((long) centerZ + row - radius);
    }

    public long cellCount() {
        return cellCount;
    }

    public boolean contains(int worldX, int worldZ) {
        long dz = (long) worldZ - centerZ;
        if (dz < -radius || dz > radius) {
            return false;
        }
        int row = Math.toIntExact(dz + radius);
        int start = rowStartX[row];
        return worldX >= start && (long) worldX < (long) start + rowLength[row];
    }

    public int cellIndex(int worldX, int worldZ) {
        if (!contains(worldX, worldZ)) {
            throw new IllegalArgumentException(
                    "world coordinate is outside the rock circle"
            );
        }
        long dz = (long) worldZ - centerZ;
        int row = Math.toIntExact(dz + radius);
        long index = Math.addExact(
                rowOffset[row],
                (long) worldX - rowStartX[row]
        );
        if (index < 0 || index >= cellCount || index > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rock circle cell index is unsupported");
        }
        return Math.toIntExact(index);
    }

    private void checkRow(int row) {
        if (row < 0 || row >= rowStartX.length) {
            throw new IndexOutOfBoundsException("invalid rock circle row: " + row);
        }
    }

    private static int floorCoordinate(double coordinate) {
        double floored = Math.floor(coordinate);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world coordinate is outside the supported block range"
            );
        }
        return (int) floored;
    }

    private static long floorSqrt(long value) {
        if (value == 0) {
            return 0;
        }
        long result = (long) Math.sqrt(value);
        while (result < value / (result + 1)) {
            result++;
        }
        while (result > value / result) {
            result--;
        }
        return result;
    }

    private static long preflightCellCount(int radius, int rowCount, long radiusSquared) {
        long radiusPlusOne = Math.addExact(radius, 1L);
        long diamondCells = Math.addExact(
                1L,
                Math.multiplyExact(2L, Math.multiplyExact(radius, radiusPlusOne))
        );
        if (diamondCells > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rock circle exceeds supported cell capacity");
        }

        long cellCount = 0;
        for (int row = 0; row < rowCount; row++) {
            long dz = (long) row - radius;
            long remaining = Math.subtractExact(radiusSquared, Math.multiplyExact(dz, dz));
            long rowLength = Math.addExact(
                    Math.multiplyExact(floorSqrt(remaining), 2L),
                    1L
            );
            cellCount = Math.addExact(cellCount, rowLength);
            if (cellCount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("rock circle exceeds supported cell capacity");
            }
        }
        return cellCount;
    }
}
