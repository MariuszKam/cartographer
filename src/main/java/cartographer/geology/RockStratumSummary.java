package cartographer.geology;

import java.util.List;

public record RockStratumSummary(
        int index,
        int size,
        int topLeftPadding,
        int bottomRightPadding,
        int innerSize,
        int samples,
        int minRawId,
        int maxRawId,
        int distinctCount,
        List<Integer> dominantRawIds
) {
    public RockStratumSummary {
        dominantRawIds =
                dominantRawIds == null
                        ? List.of()
                        : List.copyOf(
                                dominantRawIds
                        );
    }
}
