package cartographer.save;

public record SelectiveChunkStreamStats(
        int uniquePositionsRequested,
        int batchesExecuted,
        int rowsFound,
        int payloadsParsed,
        int paletteRejectedChunks,
        int fullyDecodedChunks,
        int failedChunks,
        long payloadBytes
) {
    public SelectiveChunkStreamStats {
        if (uniquePositionsRequested < 0
                || batchesExecuted < 0
                || rowsFound < 0
                || payloadsParsed < 0
                || paletteRejectedChunks < 0
                || fullyDecodedChunks < 0
                || failedChunks < 0
                || payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "selective chunk stream statistics cannot be negative"
            );
        }
    }
}
