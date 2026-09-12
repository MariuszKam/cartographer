package cartographer.scanner;

import java.util.List;
import java.util.Objects;

public record ActualBlockMap(
        String match,
        int centerWorldX,
        int centerWorldZ,
        int radius,
        long matchingBlocks,
        int minMatchedY,
        int maxMatchedY,
        List<ActualBlockMapCell> cells
) {

    public ActualBlockMap {
        if (match == null
                || match.isBlank()) {
            throw new IllegalArgumentException(
                    "ActualBlockMap match must not be blank"
            );
        }

        if (radius <= 0) {
            throw new IllegalArgumentException(
                    "ActualBlockMap radius must be positive"
            );
        }

        if (matchingBlocks < 0) {
            throw new IllegalArgumentException(
                    "ActualBlockMap matchingBlocks must not be negative"
            );
        }

        cells =
                List.copyOf(
                        Objects.requireNonNull(
                                cells,
                                "ActualBlockMap cells are required"
                        )
                );

        if (cells.isEmpty()) {
            minMatchedY =
                    -1;

            maxMatchedY =
                    -1;

        } else if (maxMatchedY < minMatchedY) {
            throw new IllegalArgumentException(
                    "ActualBlockMap maxMatchedY must be greater than or equal to minMatchedY"
            );
        }
    }

    public int diameterBlocks() {
        return radius * 2 + 1;
    }

    public int hitColumns() {
        return cells.size();
    }
}
