package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanner;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSurfaceResourceMapUseCaseTest {

    @Test
    void healthyRainHeightPathUsesDirectReadersWithoutFallback() {
        MapChunkCoordinate mapChunkCoordinate = new MapChunkCoordinate(0, 0);
        ChunkPosition exactPosition = new ChunkPosition(0, 0, 0, 0);
        FakeReader reader = new FakeReader(
                List.of(mapChunkCoordinate),
                Map.of(exactPosition, surfaceChunk(new ChunkCoordinate(0, 0, 0), 1)),
                fireClayRegistry()
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(List.of(List.of(exactPosition)), reader.exactRequests);
        assertTrue(result.analysis().matchingBlockCount() > 0);
    }

    @Test
    void missingRainHeightFallsBackOnlyThatMapChunk() {
        MapChunkCoordinate first = new MapChunkCoordinate(0, 0);
        MapChunkCoordinate second = new MapChunkCoordinate(1, 0);
        List<MapChunkCoordinate> renderMapChunks = new ArrayList<>();
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 2; x++) {
                renderMapChunks.add(new MapChunkCoordinate(x, z));
            }
        }
        Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        for (MapChunkCoordinate coordinate : renderMapChunks) {
            if (coordinate.equals(second)) {
                continue;
            }
            chunks.put(
                    new ChunkPosition(coordinate.x(), 0, coordinate.z(), 0),
                    surfaceChunk(new ChunkCoordinate(coordinate.x(), 0, coordinate.z()), 1)
            );
        }
        for (int y = 0; y < 8; y++) {
            chunks.put(
                    new ChunkPosition(1, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(1, y, 0), 1)
            );
        }
        FakeReader reader = new FakeReader(renderMapChunks, chunks, fireClayRegistry());
        reader.mapChunks.put(second, new MapChunk(second, new int[0], filledHeights(5)));

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(32, 0, 32));

        assertEquals(2, reader.exactChunkCalls);
        assertEquals(1, reader.exactRequests.getFirst().size());
        assertEquals(8, reader.exactRequests.get(1).size());
        assertTrue(result.analysis().matchingBlockCount() > 0);
    }

    @Test
    void missingRequestedMapChunkUsesLocalFallback() {
        ChunkPosition fallbackPosition = new ChunkPosition(0, 0, 0, 0);
        FakeReader reader = new FakeReader(
                List.of(),
                Map.of(fallbackPosition, surfaceChunk(new ChunkCoordinate(0, 0, 0), 1)),
                fireClayRegistry()
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(8, reader.exactRequests.getFirst().size());
        assertTrue(result.analysis().matchingBlockCount() > 0);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
    }

    @Test
    void renderOnlyMapChunkDoesNotCauseSurfaceFallback() {
        MapChunkCoordinate renderOnly = new MapChunkCoordinate(1, 1);
        FakeReader reader = new FakeReader(List.of(renderOnly), Map.of(), fireClayRegistry());
        reader.mapChunks.put(renderOnly, new MapChunk(renderOnly, new int[0], filledHeights(5)));

        useCase(reader).execute(request(16, 16, 1));

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(0, reader.exactChunkCalls);
    }

    @Test
    void fireClayStillRequiresAllTokens() {
        FakeReader reader = new FakeReader(
                List.of(new MapChunkCoordinate(0, 0)),
                Map.of(new ChunkPosition(0, 0, 0, 0), surfaceChunk(new ChunkCoordinate(0, 0, 0), 1)),
                Map.of(
                        0, new BlockInfo(0, "air"),
                        1, new BlockInfo(1, "game:fire-rock")
                )
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(0, result.analysis().matchingBlockCount());
    }

    private RenderSurfaceResourceMapRequest request(int x, int z, int radius) {
        return new RenderSurfaceResourceMapRequest(
                Path.of("save.vcdbs"), radius, 1, RenderStyle.TOPOGRAPHIC,
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                new SurfaceResourceMatch("Fire Clay", List.of("fire", "clay")),
                Optional.of(new WorldPosition(x, 100, z))
        );
    }

    private RenderSurfaceResourceMapUseCase useCase(FakeReader reader) {
        return new RenderSurfaceResourceMapUseCase(
                reader,
                new WorldMetadataReader(null, null) {
                    @Override
                    public WorldMetadata read(Path savePath) {
                        return new WorldMetadata(96, 256, 96);
                    }
                },
                new HomeStore(Path.of("build", "surface-test-home.properties")),
                new MarkerStore(Path.of("build", "surface-test-markers.csv")),
                new MapRenderer(), new UserMarkerRenderer(), new SurfaceScanner(),
                new SurfaceResourceAnalyzer(), new SurfaceResourceOverlayRenderer()
        );
    }

    private Map<Integer, BlockInfo> fireClayRegistry() {
        return Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:fire-clay-blue")
        );
    }

    private ParsedChunk surfaceChunk(ChunkCoordinate coordinate, int blockId) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int[] liquids = new int[blocks.length];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                blocks[(5 * size + z) * size + x] = blockId;
            }
        }
        return new ParsedChunk(coordinate, 0, size, size, size, blocks, liquids, 0, true, "");
    }

    private int[] filledHeights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, value);
        return heights;
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private final Map<ChunkPosition, ParsedChunk> chunks;
        private final List<List<ChunkPosition>> exactRequests = new ArrayList<>();
        private final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();
        private int directMapChunkCalls;
        private int exactChunkCalls;
        private int legacyMapChunkCalls;
        private int legacyChunkCalls;

        private FakeReader(
                List<MapChunkCoordinate> deliveredMapChunks,
                Map<ChunkPosition, ParsedChunk> chunks,
                Map<Integer, BlockInfo> registry
        ) {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
            this.chunks = chunks;
            this.registry = registry;
            for (MapChunkCoordinate coordinate : deliveredMapChunks) {
                mapChunks.put(coordinate, new MapChunk(coordinate, filledHeights(5), new int[0]));
            }
        }

        @Override
        public WorldPosition readPlayerPosition(Path savePath) {
            return new WorldPosition(16, 100, 16);
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath,
                java.util.Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer
        ) {
            directMapChunkCalls++;
            int delivered = 0;
            for (MapChunkCoordinate coordinate : coordinates) {
                MapChunk mapChunk = mapChunks.get(coordinate);
                if (mapChunk != null) {
                    delivered++;
                    consumer.accept(mapChunk);
                }
            }
            return new MapChunkStreamStats(coordinates.size(), coordinates.isEmpty() ? 0 : 1,
                    delivered, delivered, 0, 0);
        }

        @Override
        public ChunkStreamStats forEachChunkByPosition(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            exactChunkCalls++;
            exactRequests.add(List.copyOf(positions));
            int delivered = 0;
            for (ChunkPosition position : positions) {
                ParsedChunk chunk = chunks.get(position);
                if (chunk != null) {
                    delivered++;
                    consumer.accept(chunk);
                }
            }
            return new ChunkStreamStats(positions.size(), positions.isEmpty() ? 0 : 1,
                    delivered, delivered, 0, 0);
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                Path savePath, WorldPosition center, int radiusBlocks, ReadDiagnostics diagnostics
        ) {
            legacyMapChunkCalls++;
            throw new AssertionError("legacy mapchunk reader must not be used");
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath, WorldPosition center, int radiusBlocks, ReadDiagnostics diagnostics
        ) {
            legacyChunkCalls++;
            throw new AssertionError("legacy chunk reader must not be used");
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return registry;
        }
    }
}
