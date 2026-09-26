package cartographer.save;

/** Summary of one authoritative coordinate-only MAPCHUNK discovery scan. */
public record ObservedMapChunkCoordinateScanStats(
        int rowsScanned,
        int coordinatesAccepted,
        int batchesExecuted,
        boolean complete
) {
    public ObservedMapChunkCoordinateScanStats {
        if (rowsScanned < 0
                || coordinatesAccepted < 0
                || batchesExecuted < 0) {
            throw new IllegalArgumentException(
                    "coordinate scan counters must not be negative"
            );
        }
        if (coordinatesAccepted > rowsScanned) {
            throw new IllegalArgumentException(
                    "accepted coordinates cannot exceed scanned rows"
            );
        }
    }
}
