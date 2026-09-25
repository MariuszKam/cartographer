package cartographer.save;

import java.util.Objects;
import java.util.Optional;

public record ChunkStreamStats(
        int uniquePositionsRequested,
        int batchesExecuted,
        int rowsFound,
        int parsedChunks,
        int failedChunks,
        long payloadBytes,
        Optional<ChunkReadMetrics> metrics
) {
    public ChunkStreamStats(
            int uniquePositionsRequested,
            int batchesExecuted,
            int rowsFound,
            int parsedChunks,
            int failedChunks,
            long payloadBytes
    ) {
        this(
                uniquePositionsRequested,
                batchesExecuted,
                rowsFound,
                parsedChunks,
                failedChunks,
                payloadBytes,
                Optional.empty()
        );
    }

    public ChunkStreamStats {
        metrics = Objects.requireNonNull(metrics, "metrics is required");
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
