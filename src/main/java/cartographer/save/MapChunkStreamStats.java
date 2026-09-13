package cartographer.save;

public record MapChunkStreamStats(
        int uniquePositionsRequested,
        int batchesExecuted,
        int rowsFound,
        int parsedMapChunks,
        int failedMapChunks,
        long payloadBytes
) {
    public MapChunkStreamStats {
        if (uniquePositionsRequested < 0
                || batchesExecuted < 0
                || rowsFound < 0
                || parsedMapChunks < 0
                || failedMapChunks < 0
                || payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "mapchunk stream statistics cannot be negative"
            );
        }
    }
}
