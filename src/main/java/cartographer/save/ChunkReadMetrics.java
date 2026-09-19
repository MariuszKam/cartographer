package cartographer.save;

import java.util.Objects;

/**
 * Observed cost breakdown for one non-selective exact-position chunk traversal.
 *
 * <p>Timings are diagnostic observations, not performance guarantees. The
 * decode-pipeline wait bucket includes time spent inside submit while bounded
 * backpressure may drain completed decode work.</p>
 */
public record ChunkReadMetrics(
        ChunkReadStrategy strategy,
        int uniquePositionsRequested,
        int batchesExecuted,
        int statementsExecuted,
        int rowsFound,
        int parsedChunks,
        int failedChunks,
        long payloadBytes,
        long strategyProbeNanos,
        long sourceReadNanos,
        long decodePipelineWaitNanos,
        long finalDrainNanos,
        long totalNanos
) {
    public ChunkReadMetrics {
        strategy = Objects.requireNonNull(strategy, "strategy is required");
        if (uniquePositionsRequested < 0
                || batchesExecuted < 0
                || statementsExecuted < 0
                || rowsFound < 0
                || parsedChunks < 0
                || failedChunks < 0
                || payloadBytes < 0
                || strategyProbeNanos < 0
                || sourceReadNanos < 0
                || decodePipelineWaitNanos < 0
                || finalDrainNanos < 0
                || totalNanos < 0) {
            throw new IllegalArgumentException("chunk read metrics cannot be negative");
        }
    }
}
