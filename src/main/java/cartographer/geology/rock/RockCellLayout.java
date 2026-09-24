package cartographer.geology.rock;

import java.util.Objects;

/** Checked packed layout for one compact ROCK cell. */
final class RockCellLayout {
    private static final int STATE_BITS = 2;

    private final int rockOrdinalBits;
    private final int yBits;
    private final int totalBits;
    private final int rockCount;
    private final long yRangeHeight;
    private final boolean intBacked;
    private final long rockMask;
    private final long yMask;

    private RockCellLayout(
            int rockOrdinalBits,
            int yBits,
            int rockCount,
            long yRangeHeight
    ) {
        if (rockOrdinalBits < 0 || rockOrdinalBits > 62
                || yBits < 0 || yBits > 62) {
            throw new IllegalArgumentException("packed field width is invalid");
        }
        this.totalBits = Math.addExact(STATE_BITS,
                Math.addExact(rockOrdinalBits, yBits));
        if (totalBits > Long.SIZE) {
            throw new IllegalArgumentException(
                    "ROCK cell layout exceeds 64 bits"
            );
        }
        if (rockCount < 0 || yRangeHeight <= 0) {
            throw new IllegalArgumentException("packed layout domain is invalid");
        }
        if (rockOrdinalBits == 0 && rockCount > 0
                || rockOrdinalBits > 0 && rockCount >= (1L << rockOrdinalBits)) {
            throw new IllegalArgumentException("rock ordinal capacity is insufficient");
        }
        if (yBits == 0 && yRangeHeight > 1
                || yBits > 0 && yRangeHeight > (1L << Math.min(yBits, 62))) {
            if (!(yBits == 62 && yRangeHeight <= (1L << 62))) {
                throw new IllegalArgumentException("Y capacity is insufficient");
            }
        }
        this.rockOrdinalBits = rockOrdinalBits;
        this.yBits = yBits;
        this.rockCount = rockCount;
        this.yRangeHeight = yRangeHeight;
        this.intBacked = totalBits <= Integer.SIZE;
        this.rockMask = mask(rockOrdinalBits);
        this.yMask = mask(yBits);
    }

    static RockCellLayout forCatalog(int rockCount, long yRangeHeight) {
        if (rockCount < 0 || yRangeHeight <= 0) {
            throw new IllegalArgumentException("packed layout domain is invalid");
        }
        int rockBits = ceilLog2(Math.addExact((long) rockCount, 1L));
        int yBits = ceilLog2(yRangeHeight);
        return new RockCellLayout(rockBits, yBits, rockCount, yRangeHeight);
    }

    boolean intBacked() {
        return intBacked;
    }

    long pack(RockColumnState state, int rockOrdinal, long yOffset) {
        Objects.requireNonNull(state, "state is required");
        int stateCode = stateCode(state);
        if (state == RockColumnState.OBSERVED) {
            if (rockOrdinal <= 0 || rockOrdinal > rockCount
                    || yOffset < 0 || yOffset >= yRangeHeight) {
                throw new IllegalArgumentException("observed packed cell value is invalid");
            }
        } else if (rockOrdinal != 0 || yOffset != 0) {
            throw new IllegalArgumentException("non-observed packed cell has payload");
        }
        long packed = stateCode;
        packed |= ((long) rockOrdinal & rockMask) << STATE_BITS;
        packed |= (yOffset & yMask) << (STATE_BITS + rockOrdinalBits);
        return intBacked ? packed & 0xFFFF_FFFFL : packed;
    }

    RockColumnState state(long packed) {
        return switch ((int) (packed & 0b11L)) {
            case 0 -> RockColumnState.UNAVAILABLE;
            case 1 -> RockColumnState.NO_ROCK;
            case 2 -> RockColumnState.OBSERVED;
            default -> throw new IllegalArgumentException("reserved ROCK cell state");
        };
    }

    int rockOrdinal(long packed) {
        return (int) ((packed >>> STATE_BITS) & rockMask);
    }

    long yOffset(long packed) {
        return (packed >>> (STATE_BITS + rockOrdinalBits)) & yMask;
    }

    void validate(long packed) {
        RockColumnState state = state(packed);
        int ordinal = rockOrdinal(packed);
        long yOffset = yOffset(packed);
        if (state == RockColumnState.OBSERVED) {
            if (ordinal <= 0 || ordinal > rockCount || yOffset >= yRangeHeight) {
                throw new IllegalArgumentException("invalid observed packed cell");
            }
        } else if (ordinal != 0 || yOffset != 0) {
            throw new IllegalArgumentException("non-observed packed cell has payload");
        }
    }

    private static int stateCode(RockColumnState state) {
        return switch (state) {
            case UNAVAILABLE -> 0;
            case NO_ROCK -> 1;
            case OBSERVED -> 2;
        };
    }

    private static long mask(int bits) {
        return bits == 0 ? 0L : (1L << bits) - 1L;
    }

    private static int ceilLog2(long value) {
        if (value <= 1) {
            return 0;
        }
        return Long.SIZE - Long.numberOfLeadingZeros(value - 1);
    }
}
