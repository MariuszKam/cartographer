package cartographer.parser;

import java.util.Arrays;
import java.util.Objects;

public record ChunkPaletteProbe(
        int[] blockIds
) {
    public ChunkPaletteProbe {
        blockIds =
                Arrays.copyOf(
                        Objects.requireNonNull(
                                blockIds,
                                "blockIds is required"
                        ),
                        blockIds.length
                );
    }

    @Override
    public int[] blockIds() {
        return Arrays.copyOf(
                blockIds,
                blockIds.length
        );
    }

    public boolean contains(
            int blockId
    ) {
        for (int candidate : blockIds) {
            if (candidate == blockId) {
                return true;
            }
        }

        return false;
    }
}
