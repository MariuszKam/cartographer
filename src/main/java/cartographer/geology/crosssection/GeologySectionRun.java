package cartographer.geology.crosssection;

public record GeologySectionRun(
        int minYInclusive,
        int maxYExclusive,
        boolean observed,
        int blockId,
        String blockCode
) {

    public GeologySectionRun {
        if (maxYExclusive <= minYInclusive) {
            throw new IllegalArgumentException(
                    "GeologySectionRun maxYExclusive must be greater than minYInclusive"
            );
        }

        if (!observed) {
            blockId =
                    -1;

            blockCode =
                    "unavailable";

        } else if (blockCode == null
                || blockCode.isBlank()) {

            blockCode =
                    "unknown:"
                            + blockId;
        }
    }
}
