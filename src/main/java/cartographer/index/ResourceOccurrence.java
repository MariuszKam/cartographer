package cartographer.index;

import cartographer.model.ChunkPosition;

import java.util.Objects;

/**
 * Compact exact ore occurrence state for one block ID in one local X/Z column.
 *
 * <p>Each bit of {@code localYMask} represents one local Y coordinate inside
 * the 32-block-high server chunk. This preserves exact Y-filter semantics
 * without persisting one database row per matching voxel.</p>
 */
public record ResourceOccurrence(
        ChunkPosition position,
        int blockId,
        int localX,
        int localZ,
        long localYMask
) {
    private static final long VALID_Y_MASK = 0xffff_ffffL;

    public ResourceOccurrence {
        Objects.requireNonNull(position, "position is required");
        if (position.dimension() != 0) {
            throw new IllegalArgumentException(
                    "resource occurrences only support main-world dimension 0"
            );
        }
        if (blockId < 0) {
            throw new IllegalArgumentException("blockId cannot be negative");
        }
        if (localX < 0 || localX >= 32 || localZ < 0 || localZ >= 32) {
            throw new IllegalArgumentException(
                    "resource occurrence local X/Z must be within 0..31"
            );
        }
        if (localYMask == 0L || (localYMask & ~VALID_Y_MASK) != 0L) {
            throw new IllegalArgumentException(
                    "localYMask must contain at least one bit within local Y 0..31"
            );
        }
    }

}
