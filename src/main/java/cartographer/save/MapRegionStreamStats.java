package cartographer.save;

public record MapRegionStreamStats(
        int rowsFound,
        int parsedMapRegions,
        int failedMapRegions,
        int invalidRows,
        int ignoredNonMainWorldRows,
        long payloadBytes
) {
    public MapRegionStreamStats {
        if (rowsFound < 0
                || parsedMapRegions < 0
                || failedMapRegions < 0
                || invalidRows < 0
                || ignoredNonMainWorldRows < 0
                || payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "mapregion stream statistics cannot be negative"
            );
        }
    }

    public boolean complete() {
        return failedMapRegions == 0 && invalidRows == 0;
    }
}
