package cartographer.save;

public record ChunkStreamStats(
        int uniquePositionsRequested,
        int batchesExecuted,
        int rowsFound,
        int parsedChunks,
        int failedChunks,
        long payloadBytes
) {
    public ChunkStreamStats {
        if (uniquePositionsRequested < 0) {
            throw new IllegalArgumentException(
                    "uniquePositionsRequested cannot be negative"
            );
        }
        if (batchesExecuted < 0) {
            throw new IllegalArgumentException(
                    "batchesExecuted cannot be negative"
            );
        }
        if (rowsFound < 0) {
            throw new IllegalArgumentException(
                    "rowsFound cannot be negative"
            );
        }
        if (parsedChunks < 0) {
            throw new IllegalArgumentException(
                    "parsedChunks cannot be negative"
            );
        }
        if (failedChunks < 0) {
            throw new IllegalArgumentException(
                    "failedChunks cannot be negative"
            );
        }
        if (payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "payloadBytes cannot be negative"
            );
        }
    }
}
