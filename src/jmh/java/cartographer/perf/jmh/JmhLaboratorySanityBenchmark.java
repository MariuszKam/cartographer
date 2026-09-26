package cartographer.perf.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * JMH wiring sanity check only. This deterministic in-memory operation is not
 * Cartographer product performance evidence or a macro benchmark substitute.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings("unused")
public class JmhLaboratorySanityBenchmark {
    private int[] values;

    @Setup(Level.Trial)
    public void setUp() {
        values = new int[]{3, 5, 8, 13, 21, 34, 55, 89};
    }

    @Benchmark
    public long deterministicChecksum() {
        return ChunkLayerBenchmarkFixture.checksum(values);
    }
}
