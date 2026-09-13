package cartographer.geology;

import java.util.List;

/**
 * Summary of raw world-generation values in one RockStrata map.
 * These values are inputs used when determining stratum thickness, not rock identities.
 */
public record RockStratumSummary(
        int index,
        int size,
        int topLeftPadding,
        int bottomRightPadding,
        int innerSize,
        int samples,
        int minRawValue,
        int maxRawValue,
        int distinctCount,
        List<Integer> dominantRawValues
) {
    public RockStratumSummary {
        dominantRawValues =
                dominantRawValues == null
                        ? List.of()
                        : List.copyOf(
                                dominantRawValues
                        );
    }
}
