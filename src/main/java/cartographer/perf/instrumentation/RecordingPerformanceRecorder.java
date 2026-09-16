package cartographer.perf.instrumentation;

import cartographer.perf.metrics.PerformanceCounters;
import cartographer.perf.metrics.PerformanceStage;
import cartographer.perf.metrics.PerformanceStageMetrics;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-operation recorder with fixed-size mutable state. Stage timings are
 * inclusive: nested stage elapsed time contributes to each open parent. It is deliberately
 * not thread-safe; a future concurrent caller must provide its own ownership
 * or aggregation design rather than relying on global counters.
 */
public final class RecordingPerformanceRecorder implements PerformanceRecorder {
    private final MonotonicTimeSource timeSource;
    private final long[] stageDurationsNanoseconds =
            new long[PerformanceStage.values().length];
    private final boolean[] measuredStages =
            new boolean[PerformanceStage.values().length];

    private long sqliteRowsVisited;
    private long sqliteRowsAccepted;
    private long sqliteBlobBytesRead;
    private long chunksRequested;
    private long chunksFound;
    private long chunksMissing;
    private long chunksPaletteRejected;
    private long chunksDecoded;
    private long chunkDecodeFailures;
    private long mapchunksRead;
    private long mapregionsRead;
    private long zstdInputBytes;
    private long zstdOutputBytes;

    public RecordingPerformanceRecorder() {
        this(SystemMonotonicTimeSource.INSTANCE);
    }

    public RecordingPerformanceRecorder(MonotonicTimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource is required");
    }

    @Override
    public PerformanceStageScope measure(PerformanceStage stage) {
        Objects.requireNonNull(stage, "stage is required");
        return new StageScope(stage, timeSource.nanoTime());
    }

    @Override
    public void addSqliteRowsVisited(long count) {
        sqliteRowsVisited = add(sqliteRowsVisited, count, "sqliteRowsVisited");
    }

    @Override
    public void addSqliteRowsAccepted(long count) {
        sqliteRowsAccepted = add(sqliteRowsAccepted, count, "sqliteRowsAccepted");
    }

    @Override
    public void addSqliteBlobBytesRead(long bytes) {
        sqliteBlobBytesRead = add(sqliteBlobBytesRead, bytes, "sqliteBlobBytesRead");
    }

    @Override
    public void addChunksRequested(long count) {
        chunksRequested = add(chunksRequested, count, "chunksRequested");
    }

    @Override
    public void addChunksFound(long count) {
        chunksFound = add(chunksFound, count, "chunksFound");
    }

    @Override
    public void addChunksMissing(long count) {
        chunksMissing = add(chunksMissing, count, "chunksMissing");
    }

    @Override
    public void addChunksPaletteRejected(long count) {
        chunksPaletteRejected = add(chunksPaletteRejected, count, "chunksPaletteRejected");
    }

    @Override
    public void addChunksDecoded(long count) {
        chunksDecoded = add(chunksDecoded, count, "chunksDecoded");
    }

    @Override
    public void addChunkDecodeFailures(long count) {
        chunkDecodeFailures = add(chunkDecodeFailures, count, "chunkDecodeFailures");
    }

    @Override
    public void addMapchunksRead(long count) {
        mapchunksRead = add(mapchunksRead, count, "mapchunksRead");
    }

    @Override
    public void addMapregionsRead(long count) {
        mapregionsRead = add(mapregionsRead, count, "mapregionsRead");
    }

    @Override
    public void addZstdInputBytes(long bytes) {
        zstdInputBytes = add(zstdInputBytes, bytes, "zstdInputBytes");
    }

    @Override
    public void addZstdOutputBytes(long bytes) {
        zstdOutputBytes = add(zstdOutputBytes, bytes, "zstdOutputBytes");
    }

    @Override
    public Optional<PerformanceInstrumentationSnapshot> snapshot() {
        EnumMap<PerformanceStage, Long> stages = new EnumMap<>(PerformanceStage.class);
        for (PerformanceStage stage : PerformanceStage.values()) {
            if (measuredStages[stage.ordinal()]) {
                stages.put(stage, stageDurationsNanoseconds[stage.ordinal()]);
            }
        }
        return Optional.of(new PerformanceInstrumentationSnapshot(
                new PerformanceStageMetrics(stages),
                new PerformanceCounters(
                        sqliteRowsVisited,
                        sqliteRowsAccepted,
                        sqliteBlobBytesRead,
                        chunksRequested,
                        chunksFound,
                        chunksMissing,
                        chunksPaletteRejected,
                        chunksDecoded,
                        chunkDecodeFailures,
                        mapchunksRead,
                        mapregionsRead,
                        zstdInputBytes,
                        zstdOutputBytes
                )
        ));
    }

    private static long add(long current, long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " increment must not be negative");
        }
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " would overflow", overflow);
        }
    }

    private final class StageScope implements PerformanceStageScope {
        private final PerformanceStage stage;
        private final long startedAtNanoseconds;
        private boolean closed;

        private StageScope(PerformanceStage stage, long startedAtNanoseconds) {
            this.stage = stage;
            this.startedAtNanoseconds = startedAtNanoseconds;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            long elapsed;
            try {
                elapsed = Math.subtractExact(
                        timeSource.nanoTime(),
                        startedAtNanoseconds
                );
            } catch (ArithmeticException overflow) {
                throw new IllegalStateException("stage timing overflow", overflow);
            }
            if (elapsed < 0) {
                throw new IllegalStateException(
                        "monotonic time source returned a negative stage duration"
                );
            }
            int index = stage.ordinal();
            try {
                stageDurationsNanoseconds[index] = Math.addExact(
                        stageDurationsNanoseconds[index],
                        elapsed
                );
            } catch (ArithmeticException overflow) {
                throw new IllegalStateException("stage timing overflow", overflow);
            }
            measuredStages[index] = true;
        }
    }
}
