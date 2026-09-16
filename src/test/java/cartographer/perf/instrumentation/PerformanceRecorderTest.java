package cartographer.perf.instrumentation;

import cartographer.perf.metrics.PerformanceStage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceRecorderTest {
    @Test
    void noOpRecorderAcceptsTypedCallsWithoutState() {
        PerformanceRecorder recorder = PerformanceRecorder.noOp();

        recorder.addChunksDecoded(4);
        try (PerformanceStageScope ignored = recorder.measure(PerformanceStage.DECODE)) {
            // The disabled scope is intentionally inert.
        }

        assertSame(NoOpPerformanceRecorder.INSTANCE, recorder);
        assertEquals(Optional.empty(), recorder.snapshot());
    }

    @Test
    void recordingCountersAccumulateAndSnapshotIsImmutable() {
        RecordingPerformanceRecorder recorder =
                new RecordingPerformanceRecorder(() -> 100L);
        recorder.addChunksRequested(2);
        recorder.addChunksRequested(3);
        recorder.addSqliteBlobBytesRead(64);
        recorder.addChunkDecodeFailures(1);

        var snapshot = recorder.snapshot().orElseThrow();

        assertEquals(5L, snapshot.counters().chunksRequested());
        assertEquals(64L, snapshot.counters().sqliteBlobBytesRead());
        assertEquals(1L, snapshot.counters().chunkDecodeFailures());
        assertFalse(snapshot.stageMetrics().durationsNanoseconds()
                .containsKey(PerformanceStage.RENDER));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.stageMetrics().durationsNanoseconds().put(
                        PerformanceStage.RENDER,
                        1L
                )
        );
    }

    @Test
    void stageDurationsAccumulateAndNestedScopesIncludeChildTime() {
        ManualTime time = new ManualTime(10L);
        RecordingPerformanceRecorder recorder = new RecordingPerformanceRecorder(time);

        try (PerformanceStageScope outer = recorder.measure(PerformanceStage.ANALYSIS)) {
            time.advance(5L);
            try (PerformanceStageScope inner = recorder.measure(PerformanceStage.DECODE)) {
                time.advance(7L);
            }
            time.advance(3L);
        }
        try (PerformanceStageScope repeated = recorder.measure(PerformanceStage.DECODE)) {
            time.advance(2L);
        }

        var stages = recorder.snapshot().orElseThrow().stageMetrics();
        assertEquals(15L, stages.durationNanoseconds(PerformanceStage.ANALYSIS));
        assertEquals(9L, stages.durationNanoseconds(PerformanceStage.DECODE));
    }

    @Test
    void negativeTimeDeltaCannotEnterSnapshot() {
        ManualTime time = new ManualTime(10L);
        RecordingPerformanceRecorder recorder = new RecordingPerformanceRecorder(time);
        PerformanceStageScope scope = recorder.measure(PerformanceStage.RENDER);
        time.set(9L);

        assertThrows(IllegalStateException.class, scope::close);
        assertEquals(0L, recorder.snapshot().orElseThrow().stageMetrics()
                .durationNanoseconds(PerformanceStage.RENDER));
    }

    @Test
    void negativeIncrementsAreRejected() {
        RecordingPerformanceRecorder recorder = new RecordingPerformanceRecorder(() -> 0L);

        assertThrows(IllegalArgumentException.class, () -> recorder.addChunksDecoded(-1));
        assertThrows(IllegalArgumentException.class, () -> recorder.addZstdInputBytes(-1));
    }

    private static final class ManualTime implements MonotonicTimeSource {
        private long value;

        private ManualTime(long value) {
            this.value = value;
        }

        @Override
        public long nanoTime() {
            return value;
        }

        private void advance(long amount) {
            value += amount;
        }

        private void set(long value) {
            this.value = value;
        }
    }
}
