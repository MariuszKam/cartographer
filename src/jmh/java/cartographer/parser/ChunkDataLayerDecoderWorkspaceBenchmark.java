package cartographer.parser;

import cartographer.model.DecodedChunkLayer;
import cartographer.perf.jmh.ChunkLayerBenchmarkFixture;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * In-memory current decoder microbenchmark with no SQLite or save I/O. It
 * compares fresh and reused workspace lifecycles, not product-level evidence.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
@Threads(1)
@State(Scope.Thread)
@SuppressWarnings("unused")
public class ChunkDataLayerDecoderWorkspaceBenchmark {
    @Param({
            ChunkLayerBenchmarkFixture.RAW_PALETTE_19,
            ChunkLayerBenchmarkFixture.COMPRESSED_PALETTE_19
    })
    public String fixture;

    private ChunkDataLayerDecoder decoder;
    private byte[] payload;
    private ChunkDecodeWorkspace workspace;

    @Setup(Level.Trial)
    public void setUp() {
        ChunkLayerBenchmarkFixture.FixtureData data =
                ChunkLayerBenchmarkFixture.create(fixture);
        decoder = new ChunkDataLayerDecoder();
        payload = data.payload();
        ChunkLayerBenchmarkFixture.verifyPublicDecode(decoder, data);
        workspace = new ChunkDecodeWorkspace();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        workspace.close();
    }

    @Benchmark
    public DecodedChunkLayer decodeWithReusedWorkspace() {
        return decoder.decodeOwned(payload, workspace);
    }

    @Benchmark
    public DecodedChunkLayer decodeCompactWithReusedWorkspace() {
        return decoder.decodeCompactOwned(
                payload,
                0,
                payload.length,
                2,
                workspace
        );
    }

    @Benchmark
    public int decodeCompactAndReadSparseSurfaceCells() {
        DecodedChunkLayer compact =
                decoder.decodeCompactOwned(
                        payload,
                        0,
                        payload.length,
                        2,
                        workspace
                );
        return compact.valueAt(0)
                + compact.valueAt(1023)
                + compact.valueAt(16384)
                + compact.valueAt(
                        ChunkDataLayerDecoder.VALUE_COUNT - 1
                );
    }

    @Benchmark
    public int decodeMaterializedAndScanAllCells() {
        DecodedChunkLayer layer =
                decoder.decodeOwned(
                        payload,
                        2,
                        workspace
                );
        int checksum = 0;
        for (int index = 0;
             index < layer.length();
             index++) {
            checksum += layer.valueAt(index);
        }
        return checksum;
    }

    @Benchmark
    public int decodeCompactAndScanAllCells() {
        DecodedChunkLayer layer =
                decoder.decodeCompactOwned(
                        payload,
                        0,
                        payload.length,
                        2,
                        workspace
                );
        int checksum = 0;
        for (int index = 0;
             index < layer.length();
             index++) {
            checksum += layer.valueAt(index);
        }
        return checksum;
    }

    @Benchmark
    public DecodedChunkLayer decodeWithFreshWorkspace() {
        try (ChunkDecodeWorkspace freshWorkspace = new ChunkDecodeWorkspace()) {
            return decoder.decodeOwned(payload, 2, freshWorkspace);
        }
    }
}
