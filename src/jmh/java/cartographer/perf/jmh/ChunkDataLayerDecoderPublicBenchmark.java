package cartographer.perf.jmh;

import cartographer.parser.ChunkDataLayerDecoder;
import cartographer.parser.ChunkDataLayerDecoderJmhAccess;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * In-memory decoder microbenchmark with no SQLite or save I/O. The historical
 * benchmark identity is retained for cross-SHA comparison, while JMH reaches
 * the canonical owned decoder through a JMH-only package bridge.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
@Threads(1)
@State(Scope.Thread)
@SuppressWarnings("unused")
public class ChunkDataLayerDecoderPublicBenchmark {
    @Param({
            ChunkLayerBenchmarkFixture.RAW_PALETTE_19,
            ChunkLayerBenchmarkFixture.COMPRESSED_PALETTE_19
    })
    public String fixture;

    private ChunkDataLayerDecoder decoder;
    private byte[] payload;

    @Setup(Level.Trial)
    public void setUp() {
        ChunkLayerBenchmarkFixture.FixtureData data =
                ChunkLayerBenchmarkFixture.create(fixture);
        decoder = new ChunkDataLayerDecoder();
        payload = data.payload();
        ChunkLayerBenchmarkFixture.verifyPublicDecode(decoder, data);
    }

    @Benchmark
    public int[] decodePublicApi() {
        return ChunkDataLayerDecoderJmhAccess
                .decodeOwned(decoder, payload, 2)
                .toArray();
    }
}
