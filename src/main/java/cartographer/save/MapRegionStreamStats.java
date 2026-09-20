package cartographer.save;

public record MapRegionStreamStats(
        int rowsFound,
        int parsedMapRegions,
        int failedMapRegions,
        int skippedRows,
        long payloadBytes
) {
    public MapRegionStreamStats {
        if (rowsFound < 0
                || parsedMapRegions < 0
                || failedMapRegions < 0
                || skippedRows < 0
                || payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "mapregion stream statistics cannot be negative"
            );
        }
    }

    public boolean complete() {
        return failedMapRegions == 0 && skippedRows == 0;
    }
}
