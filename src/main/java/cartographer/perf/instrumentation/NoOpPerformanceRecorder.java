package cartographer.perf.instrumentation;

import cartographer.perf.metrics.PerformanceStage;

import java.util.Optional;

/** Disabled recorder used by default; it retains no state and allocates no scopes. */
public final class NoOpPerformanceRecorder implements PerformanceRecorder {
    public static final NoOpPerformanceRecorder INSTANCE =
            new NoOpPerformanceRecorder();

    private static final PerformanceStageScope NO_OP_SCOPE = () -> {
    };

    private NoOpPerformanceRecorder() {
    }

    @Override
    public PerformanceStageScope measure(PerformanceStage stage) {
        return NO_OP_SCOPE;
    }

    @Override
    public void addSqliteRowsVisited(long count) {
    }

    @Override
    public void addSqliteRowsAccepted(long count) {
    }

    @Override
    public void addSqliteBlobBytesRead(long bytes) {
    }

    @Override
    public void addChunksRequested(long count) {
    }

    @Override
    public void addChunksFound(long count) {
    }

    @Override
    public void addChunksMissing(long count) {
    }

    @Override
    public void addChunksPaletteRejected(long count) {
    }

    @Override
    public void addChunksDecoded(long count) {
    }

    @Override
    public void addChunkDecodeFailures(long count) {
    }

    @Override
    public void addMapchunksRead(long count) {
    }

    @Override
    public void addMapregionsRead(long count) {
    }

    @Override
    public void addZstdInputBytes(long bytes) {
    }

    @Override
    public void addZstdOutputBytes(long bytes) {
    }

    @Override
    public Optional<PerformanceInstrumentationSnapshot> snapshot() {
        return Optional.empty();
    }
}
