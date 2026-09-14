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
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.SurfaceObjectCandidate;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.resource.SurfaceObjectObservation;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSurfaceResourceMapUseCaseTest {

    @Test
    void healthyRainHeightPathUsesDirectReadersWithoutFallback() {
        MapChunkCoordinate mapChunkCoordinate = new MapChunkCoordinate(0, 0);
        ChunkPosition exactPosition = new ChunkPosition(0, 0, 0, 0);
        FakeReader reader = new FakeReader(
                List.of(mapChunkCoordinate),
                Map.of(exactPosition, surfaceChunk(new ChunkCoordinate(0, 0, 0))),
                fireClayRegistry()
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(List.of(List.of(exactPosition)), reader.exactRequests);
        assertTrue(result.analysis().matchingBlockCount() > 0);
    }

    @Test
    void observedSurfaceObjectRenderReusesDiscoveryWithoutSelectiveScan() {
        MapChunkCoordinate mapChunkCoordinate = new MapChunkCoordinate(0, 0);
        ChunkPosition exactPosition = new ChunkPosition(0, 0, 0, 0);
        Map<Integer, BlockInfo> registry = Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:soil-grass"),
                7, new BlockInfo(7, "game:loosestones-obsidian-free")
        );
        FakeReader reader = new FakeReader(
                List.of(mapChunkCoordinate),
                Map.of(exactPosition, surfaceChunkWithObsidian()),
                registry
        );
        SurfaceObjectCandidate candidate = new SurfaceObjectCandidateCatalogBuilder()
                .build(registry)
                .findByBlockId(7)
                .orElseThrow();
        ObservedSurfaceResource observed = new ObservedSurfaceResource(
                candidate,
                List.of(new SurfaceObjectObservation(candidate, 16, 6, 16, 7))
        );
        RenderSurfaceResourceMapRequest request =
                RenderSurfaceResourceMapRequest.forObservedResource(
                        Path.of("save.vcdbs"),
                        1,
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                        observed,
                        new WorldPosition(16, 100, 16)
                );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(
                request,
                observed,
                cartographer.application.ProgressReporter.NONE
        );

        assertEquals(0, reader.coverageCalls);
        assertEquals(1, result.analysis().matchingBlockCount());
    }

    @Test
    void missingRainHeightFallsBackOnlyThatMapChunk() {
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
                    surfaceChunk(new ChunkCoordinate(coordinate.x(), 0, coordinate.z()))
            );
        }
        for (int y = 0; y < 8; y++) {
            chunks.put(
                    new ChunkPosition(1, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(1, y, 0))
            );
        }
        FakeReader reader = new FakeReader(renderMapChunks, chunks, fireClayRegistry());
        reader.mapChunks.put(second, new MapChunk(second, new int[0], filledHeights()));

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(32, 0, 32));

        assertEquals(2, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream().noneMatch(
                position -> position.x() == second.x() && position.z() == second.z()
        ));
        Set<ChunkPosition> expectedFallback = new HashSet<>();
        for (int y = 0; y < 8; y++) {
            expectedFallback.add(new ChunkPosition(second.x(), y, second.z(), 0));
        }
        assertEquals(expectedFallback, Set.copyOf(reader.exactRequests.get(1)));
        assertTrue(reader.exactRequests.get(1).stream().allMatch(
                position -> position.x() == second.x() && position.z() == second.z()
        ));
        assertTrue(result.analysis().matchingBlockCount() > 0);
    }

    @Test
    void missingRequestedMapChunkUsesLocalFallback() {
        ChunkPosition fallbackPosition = new ChunkPosition(0, 0, 0, 0);
        FakeReader reader = new FakeReader(
                List.of(),
                Map.of(fallbackPosition, surfaceChunk(new ChunkCoordinate(0, 0, 0))),
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
        MapChunkCoordinate healthy = new MapChunkCoordinate(0, 0);
        MapChunkCoordinate renderOnly = new MapChunkCoordinate(1, 1);
        FakeReader reader = new FakeReader(
                List.of(healthy, renderOnly),
                Map.of(new ChunkPosition(0, 0, 0, 0), surfaceChunk(new ChunkCoordinate(0, 0, 0))),
                fireClayRegistry()
        );
        reader.mapChunks.put(renderOnly, new MapChunk(renderOnly, new int[0], filledHeights()));

        useCase(reader).execute(request(16, 16, 1));

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(
                List.of(new ChunkPosition(0, 0, 0, 0)),
                reader.exactRequests.getFirst()
        );
    }

    @Test
    void surfaceSearchOutsideRenderWindowStillUsesFastMapChunkLookup() {
        List<MapChunkCoordinate> surfaceCoordinates = new ArrayList<>();
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 1; x++) {
                surfaceCoordinates.add(new MapChunkCoordinate(x, z));
            }
        }
        MapChunkCoordinate surfaceOnly = new MapChunkCoordinate(2, 1);
        surfaceCoordinates.add(surfaceOnly);
        Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        for (MapChunkCoordinate coordinate : surfaceCoordinates) {
            chunks.put(
                    new ChunkPosition(coordinate.x(), 0, coordinate.z(), 0),
                    surfaceChunk(new ChunkCoordinate(coordinate.x(), 0, coordinate.z()))
            );
        }
        FakeReader reader = new FakeReader(surfaceCoordinates, chunks, fireClayRegistry());

        useCase(reader, new WorldMetadata(128, 256, 128)).execute(
                request(31.6, 48.0, 32)
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertTrue(reader.directMapChunkRequests.getFirst().contains(surfaceOnly));
        assertEquals(1, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream().anyMatch(
                position -> position.x() == surfaceOnly.x()
                        && position.z() == surfaceOnly.z()
        ));
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
    }

    @Test
    void unresolvedFastTargetFallsBackWholeMapChunk() {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        ChunkPosition position = new ChunkPosition(0, 0, 0, 0);
        FakeReader reader = new FakeReader(
                List.of(coordinate),
                Map.of(position, surfaceChunk(coordinateToChunk(position), false)),
                fireClayRegistry()
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(2, reader.adaptiveExactChunkCalls);
        assertEquals(2, reader.exactChunkCalls);
        assertEquals(List.of(position), reader.exactRequests.getFirst());
        Set<ChunkPosition> expectedFallback = new HashSet<>();
        for (int y = 0; y < 8; y++) {
            expectedFallback.add(new ChunkPosition(0, y, 0, 0));
        }
        assertEquals(expectedFallback, Set.copyOf(reader.exactRequests.get(1)));
        assertTrue(result.analysis().matchingBlockCount() > 0);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
    }

    @Test
    void resultSurfaceContainsMergedFastAndFallbackBlocks() {
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 0);
        List<MapChunkCoordinate> coordinates = List.of(
                new MapChunkCoordinate(0, 0), new MapChunkCoordinate(1, 0),
                new MapChunkCoordinate(2, 0), new MapChunkCoordinate(0, 1),
                new MapChunkCoordinate(1, 1), new MapChunkCoordinate(2, 1)
        );
        Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            chunks.put(
                    new ChunkPosition(coordinate.x(), 0, coordinate.z(), 0),
                    surfaceChunk(new ChunkCoordinate(coordinate.x(), 0, coordinate.z()))
            );
        }
        FakeReader reader = new FakeReader(coordinates, chunks, fireClayRegistry());
        reader.mapChunks.put(fallback, new MapChunk(fallback, new int[0], filledHeights()));

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(32, 0, 32));

        assertTrue(result.surface().blocks().stream().anyMatch(block -> block.worldX() < 32));
        assertTrue(result.surface().blocks().stream().anyMatch(block -> block.worldX() >= 32));
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
    }

    @Test
    void fireClayStillRequiresAllTokens() {
        Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        chunks.put(
                new ChunkPosition(0, 0, 0, 0),
                surfaceChunkWithSpecial(
                        new ChunkCoordinate(0, 0, 0)
                )
        );
        FakeReader reader = new FakeReader(
                List.of(new MapChunkCoordinate(0, 0)),
                chunks,
                Map.of(
                        0, new BlockInfo(0, "air"),
                        1, new BlockInfo(1, "game:fire-rock"),
                        2, new BlockInfo(2, "game:fire-clay-blue")
                )
        );

        RenderSurfaceResourceMapResult result = useCase(reader).execute(request(16, 16, 1));

        assertEquals(1, result.analysis().matchingBlockCount());
    }

    private RenderSurfaceResourceMapRequest request(double x, double z, int radius) {
        return new RenderSurfaceResourceMapRequest(
                Path.of("save.vcdbs"), radius, 1, RenderStyle.TOPOGRAPHIC,
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                new SurfaceMaterialMatch("Fire Clay", List.of("fire", "clay")),
                Optional.of(new WorldPosition(x, 100, z))
        );
    }

    private RenderSurfaceResourceMapUseCase useCase(FakeReader reader) {
        return useCase(reader, new WorldMetadata(96, 256, 96));
    }

    private RenderSurfaceResourceMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata
    ) {
        return new RenderSurfaceResourceMapUseCase(
                reader,
                new WorldMetadataReader(null, null) {
                    @Override
                    public WorldMetadata read(Path savePath) {
                        return metadata;
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

    private ParsedChunk surfaceChunk(ChunkCoordinate coordinate) {
        return surfaceChunk(coordinate, true);
    }

    private ParsedChunk surfaceChunkWithObsidian() {
        ParsedChunk base = surfaceChunk(new ChunkCoordinate(0, 0, 0));
        int[] blocks = base.blockIds();
        blocks[(6 * 32 + 16) * 32 + 16] = 7;
        return new ParsedChunk(
                base.coordinate(), base.minY(), base.sizeX(), base.sizeY(), base.sizeZ(),
                blocks, base.liquidIds(), 0, true, ""
        );
    }

    private ParsedChunk surfaceChunk(
            ChunkCoordinate coordinate,
            boolean liquidAvailable
    ) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int[] liquids = new int[blocks.length];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                blocks[(5 * size + z) * size + x] = 1;
            }
        }
        return new ParsedChunk(coordinate, 0, size, size, size, blocks, liquids, 0, liquidAvailable, "");
    }

    private ParsedChunk surfaceChunkWithSpecial(
            ChunkCoordinate coordinate
    ) {
        ParsedChunk base = surfaceChunk(coordinate);
        int[] blocks = base.blockIds();
        blocks[(5 * 32 + 16) * 32 + 16] = 2;
        return new ParsedChunk(coordinate, 0, 32, 32, 32, blocks, base.liquidIds(), 0, true, "");
    }

    private ChunkCoordinate coordinateToChunk(ChunkPosition position) {
        return new ChunkCoordinate(position.x(), position.y(), position.z());
    }

    private static int[] filledHeights() {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, 5);
        return heights;
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private final Map<ChunkPosition, ParsedChunk> chunks;
        private final List<List<ChunkPosition>> exactRequests = new ArrayList<>();
        private final List<List<MapChunkCoordinate>> directMapChunkRequests = new ArrayList<>();
        private final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();
        private int directMapChunkCalls;
        private int exactChunkCalls;
        private int adaptiveExactChunkCalls;
        private int coverageCalls;
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
                mapChunks.put(coordinate, new MapChunk(coordinate, filledHeights(), new int[0]));
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
            directMapChunkRequests.add(List.copyOf(coordinates));
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
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath,
                java.util.Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            return forEachMapChunkByCoordinate(
                    savePath, coordinates, diagnostics, consumer
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
            java.util.function.Consumer<ParsedChunk> consumer
        ) {
            adaptiveExactChunkCalls++;
            return forEachChunkByPosition(
                    savePath, positions, diagnostics, consumer
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            return forEachChunkByPositionAdaptive(
                    savePath, positions, diagnostics, consumer
            );
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
        public cartographer.save.SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<cartographer.save.SelectiveChunkVisit> consumer
        ) {
            coverageCalls++;
            int decoded = 0;
            int rejected = 0;
            for (ChunkPosition position : positions) {
                ParsedChunk chunk = chunks.get(position);
                if (chunk == null) {
                    consumer.accept(cartographer.save.SelectiveChunkVisit.missing(position));
                    continue;
                }
                boolean containsWanted = false;
                for (int blockId : chunk.blockIds()) {
                    for (int wantedBlockId : wantedBlockIds) {
                        if (blockId == wantedBlockId) {
                            containsWanted = true;
                            break;
                        }
                    }
                    if (containsWanted) {
                        break;
                    }
                }
                if (containsWanted) {
                    decoded++;
                    consumer.accept(cartographer.save.SelectiveChunkVisit.decoded(position, chunk));
                } else {
                    rejected++;
                    consumer.accept(cartographer.save.SelectiveChunkVisit.paletteRejected(position));
                }
            }
            return new cartographer.save.SelectiveChunkStreamStats(
                    positions.size(), positions.isEmpty() ? 0 : 1,
                    decoded + rejected, decoded, rejected, decoded, 0, 0
            );
        }

        @Override
        public cartographer.save.SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<cartographer.save.SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    savePath, positions, wantedBlockIds, diagnostics, consumer
            );
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
