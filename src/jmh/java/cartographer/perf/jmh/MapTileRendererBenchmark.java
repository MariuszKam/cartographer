package cartographer.perf.jmh;

import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.render.MapTileRenderer;
import cartographer.render.RenderLod;
import cartographer.render.RenderStyle;
import cartographer.render.RenderTileBounds;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileLayout;
import cartographer.render.RenderedMapTile;
import cartographer.render.TerrainColorRange;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings("unused")
public class MapTileRendererBenchmark {
    @Param({"4", "8"})
    public int mapChunksPerSide;

    @Param({"1", "4", "16"})
    public int blocksPerPixel;

    private MapTileRenderer renderer;
    private RenderTileCoordinate coordinate;
    private RenderTileData data;
    private Map<MapChunkCoordinate, TerrainHeightTile> context;
    private TerrainColorRange range;

    @Setup(Level.Trial)
    public void setUp() {
        renderer = new MapTileRenderer();
        coordinate = new RenderTileCoordinate(0, 0);
        RenderTileLayout layout = new RenderTileLayout(mapChunksPerSide);
        RenderTileBounds bounds = layout.boundsFor(coordinate);
        LinkedHashMap<MapChunkCoordinate, TerrainHeightTile> terrain =
                new LinkedHashMap<>();

        for (MapChunkCoordinate mapChunk : layout.mapChunksFor(coordinate)) {
            int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
            for (int z = 0; z < MapChunk.SIZE; z++) {
                for (int x = 0; x < MapChunk.SIZE; x++) {
                    heights[z * MapChunk.SIZE + x] =
                            80 + Math.floorMod(
                                    mapChunk.x() * 13
                                            + mapChunk.z() * 17
                                            + x * 3
                                            + z * 5,
                                    96
                            );
                }
            }
            terrain.put(
                    mapChunk,
                    TerrainHeightTile.from(
                            new MapChunk(
                                    mapChunk,
                                    heights,
                                    new int[0]
                            )
                    )
            );
        }

        context = Map.copyOf(terrain);
        range = TerrainColorRange.fromTiles(terrain.values());
        data = new RenderTileData(
                bounds,
                Optional.of(bounds),
                layout.mapChunksFor(coordinate),
                terrain,
                Map.of()
        );
    }

    @Benchmark
    public RenderedMapTile renderTile() {
        return renderer.render(
                coordinate,
                data,
                context,
                range,
                RenderStyle.TOPOGRAPHIC,
                java.util.Set.of(cartographer.render.RenderLayer.TERRAIN),
                java.util.Map.of(),
                RenderLod.forBlocksPerPixel(blocksPerPixel)
        );
    }
}
