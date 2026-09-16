package cartographer.perf.instrumentation;

import cartographer.perf.metrics.PerformanceStage;

import java.util.Optional;

/**
 * Neutral, typed instrumentation contract. One recording instance belongs to
 * one operation or measurement run; it is not a global metrics registry.
 */
public interface PerformanceRecorder {
    PerformanceRecorder NO_OP = NoOpPerformanceRecorder.INSTANCE;

    static PerformanceRecorder noOp() {
        return NO_OP;
    }

    PerformanceStageScope measure(PerformanceStage stage);

    void addSqliteRowsVisited(long count);

    void addSqliteRowsAccepted(long count);

    void addSqliteBlobBytesRead(long bytes);

    void addChunksRequested(long count);

    void addChunksFound(long count);

    void addChunksMissing(long count);

    void addChunksPaletteRejected(long count);

    void addChunksDecoded(long count);

    void addChunkDecodeFailures(long count);

    void addMapchunksRead(long count);

    void addMapregionsRead(long count);

    void addZstdInputBytes(long bytes);

    void addZstdOutputBytes(long bytes);

    /** Empty for a no-op recorder; present only when data was recorded. */
    Optional<PerformanceInstrumentationSnapshot> snapshot();
}
