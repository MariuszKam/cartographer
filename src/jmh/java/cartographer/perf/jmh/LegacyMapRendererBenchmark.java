package cartographer.perf.jmh;

import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldPosition;
import cartographer.progress.ProgressReporter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Phase-0 baseline for the legacy whole-map renderer.
 *
 * <p>The current renderer publishes no usable map image before render returns,
 * so this operation's completion latency is also the legacy model's time to
 * first visible map. The benchmark intentionally excludes SQLite/source I/O
 * and measures deterministic in-memory rendering only.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings("unused")
public class LegacyMapRendererBenchmark {
    @Param({"128", "256", "512"})
    public int radiusBlocks;

    private MapRenderer renderer;
    private WorldPosition center;
    private List<MapChunk> chunks;
    private RenderOptions options;

    @Setup(Level.Trial)
    public void setUp() {
        renderer = new MapRenderer();
        center = new WorldPosition(0.0, 0.0, 0.0);
        chunks = chunksFor(radiusBlocks);
        options = new RenderOptions(
                radiusBlocks,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN)
        );
    }

    @Benchmark
    public RenderedMap legacyWholeMapRender() {
        return renderer.render(
                center,
                HomeState.absent(),
                chunks,
                options,
                ProgressReporter.NONE
        );
    }

    private static List<MapChunk> chunksFor(int radiusBlocks) {
        if (radiusBlocks % MapChunk.SIZE != 0) {
            throw new IllegalArgumentException(
                    "radiusBlocks must align to mapchunk size"
            );
        }
        int radiusInMapChunks = radiusBlocks / MapChunk.SIZE;
        int diameterInMapChunks = radiusInMapChunks * 2;
        List<MapChunk> chunks = new ArrayList<>(
                diameterInMapChunks * diameterInMapChunks
        );
        for (int chunkZ = -radiusInMapChunks;
             chunkZ < radiusInMapChunks;
             chunkZ++) {
            for (int chunkX = -radiusInMapChunks;
                 chunkX < radiusInMapChunks;
                 chunkX++) {
                chunks.add(chunk(chunkX, chunkZ));
            }
        }
        return List.copyOf(chunks);
    }

    private static MapChunk chunk(int chunkX, int chunkZ) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
            for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                int worldX = chunkX * MapChunk.SIZE + localX;
                int worldZ = chunkZ * MapChunk.SIZE + localZ;
                heights[localZ * MapChunk.SIZE + localX] = 96 + Math.floorMod(
                        worldX * 7 + worldZ * 11 + chunkX * 13 - chunkZ * 5,
                        80
                );
            }
        }
        return new MapChunk(
                new MapChunkCoordinate(chunkX, chunkZ),
                heights,
                new int[0]
        );
    }
}
