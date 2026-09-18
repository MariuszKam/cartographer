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
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderActualOreMapUseCaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void renderRequestRequiresNonNullOptionalReferences() {
        assertEquals(
                Optional.empty(),
                new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"),
                        1,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(),
                        Optional.empty(),
                        ActualBlockYFilter.unbounded(),
                        Optional.empty()
                ).oreMatch()
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), null, ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.empty(), ActualBlockYFilter.unbounded(), null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.of(" "), ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
    }

    @Test
    void renderResultRequiresNonNullActualOreMapOptional() {
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapResult(
                        null, null, null, null, null, null, null,
                        null, null, null, null, 0, List.of()
                )
        );
    }

    @Test
    void usesSelectiveReaderForOreAndStreamsMatchingChunk() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapResult result = execute(reader);

        assertEquals(1, reader.adaptiveSelectiveCalls);
        assertEquals(1, reader.selectiveCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertArrayEquals(new int[]{1}, reader.lastWantedBlockIds);
        assertFalse(reader.lastPositions.isEmpty());
        assertEquals(1, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(64, result.geometry().imageWidth());
        assertEquals(48.0, result.geometry().worldMinX());
        assertEquals(48.0, result.geometry().worldMinZ());
        assertEquals(80.0, result.geometry().worldMaxXExclusive());
        assertEquals(80.0, result.geometry().worldMaxZExclusive());
        assertEquals(
                ActualBlockMatchMode.ORE_CODE,
                result.actualOreOverlays().getFirst().spec().matchMode()
        );
    }

    @Test
    void avoidsSelectiveReaderWhenRegistryHasNoMatchingIds() {
        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapResult result = execute(reader);

        assertEquals(0, reader.selectiveCalls);
        assertEquals(0, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.actualOreOverlays().getFirst().map().cells().isEmpty());
    }

    @Test
    void twoOverlaysPreserveOrderAndUseOneSelectiveLookup() {
        FakeReader reader = new FakeReader(Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "ore-nativecopper-granite")
        ));
        RenderActualOreMapResult result = execute(
                reader,
                List.of(
                        new ActualOreOverlaySpec("Cassiterite", "cassiterite", Color.ORANGE, ActualBlockMatchMode.ORE_CODE),
                        new ActualOreOverlaySpec("Copper", "nativecopper", Color.RED, ActualBlockMatchMode.ORE_CODE)
                )
        );

        assertEquals(1, reader.selectiveCalls);
        assertEquals("Cassiterite", result.actualOreOverlays().get(0).spec().displayName());
        assertEquals("Copper", result.actualOreOverlays().get(1).spec().displayName());
    }

    @Test
    void genericSubstringMatchesNonOreCodeButOreCodeDoesNot() {
        FakeReader genericReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult generic = execute(
                genericReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.GENERIC_SUBSTRING
                ))
        );
        FakeReader oreReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult ore = execute(
                oreReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );

        assertEquals(1, generic.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(0, oreReader.selectiveCalls);
        assertEquals(0, ore.actualOreOverlays().getFirst().map().matchingBlocks());
    }

    @Test
    void surfaceDisabledDoesNotReadSurfaceChunks() {
        FakeReader reader = new FakeReader(Map.of());

        execute(reader, List.of(), Set.of(RenderLayer.TERRAIN), 64, 64, 16);

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(0, reader.exactChunkCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(0, reader.registryCalls);
        assertEquals(0, reader.pathPlayerCalls);
        assertEquals(0, reader.pathMapChunkCalls);
        assertEquals(0, reader.pathRegistryCalls);
    }

    @Test
    void environmentOverlayUsesSessionMapRegionReader() {
        FakeReader reader = new FakeReader(Map.of());

        execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT),
                64,
                64,
                16
        );

        assertEquals(1, reader.sessionMapRegionCalls);
        assertEquals(0, reader.pathMapRegionCalls);
    }

    @Test
    void surfaceLayerUsesRainHeightFastPath() {
        FakeReader reader = surfaceReader(true);

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.exactRequests.getFirst().size());
        assertTrue(hasSurfaceBlockId(result, 1));
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(0, reader.pathPlayerCalls);
        assertEquals(0, reader.pathMapChunkCalls);
        assertEquals(0, reader.pathAdaptiveChunkCalls);
        assertEquals(0, reader.pathAdaptiveSelectiveCalls);
        assertEquals(0, reader.pathRegistryCalls);
    }

    @Test
    void soilFertilityOnlyUsesTheExistingSurfacePipeline() {
        FakeReader reader = fertilityReader();

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.SOIL_FERTILITY),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.registryCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertTrue(hasSurfaceCode(result, "game:soil-medium-normal"));
        assertTrue(result.image().getRGB(32, 32)
                != new cartographer.render.TerrainPalette()
                .background(RenderStyle.TOPOGRAPHIC));
    }

    @Test
    void surfaceAndSoilFertilityShareOneSurfacePipeline() {
        FakeReader reader = fertilityReader();

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.SURFACE, RenderLayer.SOIL_FERTILITY),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.registryCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertTrue(hasSurfaceCode(result, "game:soil-medium-normal"));
    }

    @Test
    void surfaceFallbackIsLocalToProblematicMapChunk() {
        FakeReader reader = surfaceReader(true);
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 0);
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 2; x++) {
                MapChunkCoordinate coordinate = new MapChunkCoordinate(x, z);
                if (coordinate.equals(fallback)) {
                    continue;
                }
                reader.mapChunks.put(
                        coordinate,
                        new MapChunk(coordinate, filledHeights(), new int[0])
                );
                reader.chunks.put(
                        new ChunkPosition(x, 0, z, 0),
                        surfaceChunk(new ChunkCoordinate(x, 0, z), true)
                );
            }
        }
        reader.mapChunks.put(fallback, new MapChunk(fallback, new int[0], filledHeights()));
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(fallback.x(), y, fallback.z(), 0),
                    surfaceChunk(new ChunkCoordinate(fallback.x(), y, fallback.z()), true)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                32,
                0,
                32
        );

        assertEquals(2, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream()
                .noneMatch(position -> position.x() == fallback.x()
                        && position.z() == fallback.z()));
        assertEquals(8, reader.exactRequests.get(1).size());
        assertTrue(reader.exactRequests.get(1).stream()
                .allMatch(position -> position.x() == fallback.x()
                        && position.z() == fallback.z()));
        assertTrue(hasSurfaceXLessThan(result, 32));
        assertTrue(hasSurfaceXAtLeast(result, 32));
    }

    @Test
    void fractionalCenterSurfaceSearchUsesUnionMapChunkLookup() {
        FakeReader reader = new FakeReader(Map.of());
        MapChunkCoordinate surfaceOnly = new MapChunkCoordinate(2, 1);
        reader.mapChunks.put(surfaceOnly, new MapChunk(surfaceOnly, filledHeights(), new int[0]));
        reader.chunks.put(
                new ChunkPosition(2, 0, 1, 0),
                surfaceChunk(new ChunkCoordinate(2, 0, 1), true)
        );
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 1; x++) {
                MapChunkCoordinate coordinate = new MapChunkCoordinate(x, z);
                reader.mapChunks.put(coordinate, new MapChunk(coordinate, filledHeights(), new int[0]));
                reader.chunks.put(
                        new ChunkPosition(x, 0, z, 0),
                        surfaceChunk(new ChunkCoordinate(x, 0, z), true)
                );
            }
        }

        execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                31.6,
                48.0,
                32,
                new WorldMetadata(128, 256, 128)
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertTrue(reader.directMapChunkRequests.getFirst().contains(surfaceOnly));
        assertEquals(1, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream()
                .anyMatch(position -> position.x() == surfaceOnly.x()
                        && position.z() == surfaceOnly.z()));
    }

    @Test
    void unresolvedFastTargetFallsBackWholeMapChunk() {
        FakeReader reader = surfaceReader(false);
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(0, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(0, y, 0), false)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                16,
                16,
                1
        );

        assertEquals(2, reader.exactChunkCalls);
        assertEquals(8, reader.exactRequests.get(1).size());
        assertTrue(result.actualOreMap().isEmpty());
        assertTrue(hasSurfaceBlockId(result, 1));
    }

    @Test
    void resultSurfaceContainsMergedFastAndFallbackBlocks() {
        FakeReader reader = surfaceReader(true);
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 0);
        reader.mapChunks.put(fallback, new MapChunk(fallback, new int[0], filledHeights()));
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(1, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(1, y, 0), true)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                32,
                0,
                32
        );

        assertTrue(hasSurfaceXLessThan(result, 32));
        assertTrue(hasSurfaceXAtLeast(result, 32));
    }

    private RenderActualOreMapResult execute(FakeReader reader) {
        return execute(
                reader,
                List.of(new ActualOreOverlaySpec(
                        "Cassiterite",
                        "cassiterite",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );
    }

    private boolean hasSurfaceBlockId(RenderActualOreMapResult result, int id) {
        int[] found = {0};
        result.surface().map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (blockId == id) found[0]++;
        });
        return found[0] != 0;
    }

    private boolean hasSurfaceCode(RenderActualOreMapResult result, String code) {
        int[] found = {0};
        result.surface().map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            BlockInfo info = result.surface().registry().get(blockId);
            if (info != null && code.equals(info.code())) found[0]++;
        });
        return found[0] != 0;
    }

    private boolean hasSurfaceXLessThan(RenderActualOreMapResult result, int bound) {
        int[] found = {0};
        result.surface().map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (x < bound) found[0]++;
        });
        return found[0] != 0;
    }

    private boolean hasSurfaceXAtLeast(RenderActualOreMapResult result, int bound) {
        int[] found = {0};
        result.surface().map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (x >= bound) found[0]++;
        });
        return found[0] != 0;
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs
    ) {
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("home.properties"),
                temporaryDirectory.resolve("markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                specs
        );
        return useCase.execute(request);
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs,
            Set<RenderLayer> layers,
            double centerX,
            double centerZ,
            int radius
    ) {
        return execute(reader, specs, layers, centerX, centerZ, radius,
                new WorldMetadata(128, 256, 128));
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs,
            Set<RenderLayer> layers,
            double centerX,
            double centerZ,
            int radius,
            WorldMetadata metadata
    ) {
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                metadata,
                temporaryDirectory.resolve("home-surface.properties"),
                temporaryDirectory.resolve("markers-surface.csv")
        );
        return useCase.execute(new RenderActualOreMapRequest(
                temporaryDirectory.resolve("surface-save.vcdbs"), radius, 1,
                RenderStyle.TOPOGRAPHIC, layers, Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(centerX, 64, centerZ)), specs
        ));
    }

    private RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            public WorldMetadata read(Path savePath) {
                return metadata;
            }

            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                new HomeStore(homePath),
                new MarkerStore(markerPath),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader)
        );
    }

    private FakeReader surfaceReader(boolean liquidAvailable) {
        FakeReader reader = new FakeReader(fireClayRegistry());
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        reader.mapChunks.put(
                coordinate,
                new MapChunk(coordinate, filledHeights(), new int[0])
        );
        reader.chunks.put(
                new ChunkPosition(0, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(0, 0, 0), liquidAvailable)
        );
        return reader;
    }

    private FakeReader fertilityReader() {
        FakeReader reader = new FakeReader(Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:soil-medium-normal")
        ));
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        reader.mapChunks.put(
                coordinate,
                new MapChunk(coordinate, filledHeights(), new int[0])
        );
        reader.chunks.put(
                new ChunkPosition(0, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(0, 0, 0), true)
        );
        return reader;
    }

    private Map<Integer, BlockInfo> fireClayRegistry() {
        return Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:fire-clay-blue")
        );
    }

    private ParsedChunk surfaceChunk(
            ChunkCoordinate coordinate,
            boolean liquidAvailable
    ) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int[] liquids = liquidAvailable ? new int[blocks.length] : null;
        String liquidDecodeError = liquidAvailable
                ? ""
                : "liquid layer unavailable for test";
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                blocks[(5 * size + z) * size + x] = 1;
            }
        }
        return new ParsedChunk(coordinate, 0, size, size, size, blocks, liquids,
                0, liquidAvailable, liquidDecodeError);
    }

    private static int[] filledHeights() {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, 5);
        return heights;
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private final Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        private final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();
        private final List<List<MapChunkCoordinate>> directMapChunkRequests = new ArrayList<>();
        private final List<List<ChunkPosition>> exactRequests = new ArrayList<>();
        private int selectiveCalls;
        private int adaptiveSelectiveCalls;
        private int directMapChunkCalls;
        private int exactChunkCalls;
        private int adaptiveExactChunkCalls;
        private int registryCalls;
        private int pathMapChunkCalls;
        private int pathAdaptiveChunkCalls;
        private int pathAdaptiveSelectiveCalls;
        private int pathPlayerCalls;
        private int pathRegistryCalls;
        private int pathMapRegionCalls;
        private int sessionMapRegionCalls;
        private int legacyMapChunkCalls;
        private int legacyChunkCalls;
        private final int fakeBlockId;
        private int[] lastWantedBlockIds = new int[0];
        private List<cartographer.model.ChunkPosition> lastPositions = List.of();

        private FakeReader(Map<Integer, BlockInfo> registry) {
            this(registry, 1);
        }

        private FakeReader(Map<Integer, BlockInfo> registry, int fakeBlockId) {
            super(null, null, null, null);
            this.registry = registry;
            this.fakeBlockId = fakeBlockId;
        }

        @Override
        public WorldPosition readPlayerPosition(Path savePath) {
            pathPlayerCalls++;
            return new WorldPosition(64, 64, 64);
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(64, 64, 64);
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath,
                java.util.Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer
        ) {
            pathMapChunkCalls++;
            directMapChunkRequests.add(List.copyOf(coordinates));
            return visitMapChunks(coordinates, consumer);
        }

        private MapChunkStreamStats visitMapChunks(
                java.util.Collection<MapChunkCoordinate> coordinates,
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
            return new MapChunkStreamStats(
                    coordinates.size(),
                    coordinates.isEmpty() ? 0 : 1,
                    delivered,
                    delivered,
                    0,
                    0
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                java.util.Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            directMapChunkRequests.add(List.copyOf(coordinates));
            return visitMapChunks(coordinates, consumer);
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
            pathAdaptiveChunkCalls++;
            return visitChunks(positions, consumer);
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveExactChunkCalls++;
            return visitChunks(positions, consumer);
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
            return visitChunks(positions, consumer);
        }

        private ChunkStreamStats visitChunks(
                java.util.Collection<ChunkPosition> positions,
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
            return new ChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    delivered,
                    delivered,
                    0,
                    0
            );
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                Path savePath,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics
        ) {
            legacyMapChunkCalls++;
            throw new AssertionError("legacy mapchunk lookup must not be used");
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics
        ) {
            legacyChunkCalls++;
            throw new AssertionError("legacy chunk lookup must not be used");
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            pathRegistryCalls++;
            return registry;
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            registryCalls++;
            return registry;
        }

        @Override
        public List<cartographer.model.ServerMapRegion> readMapRegions(
                Path savePath,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            pathMapRegionCalls++;
            return List.of();
        }

        @Override
        public List<cartographer.model.ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            sessionMapRegionCalls++;
            return List.of();
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            pathAdaptiveSelectiveCalls++;
            return visitSelective(positions, wantedBlockIds, consumer);
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveSelectiveCalls++;
            return visitSelective(positions, wantedBlockIds, consumer);
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            return forEachChunkByPositionMatchingBlockIdsAdaptive(
                    savePath, positions, wantedBlockIds, diagnostics, consumer
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
                Path savePath,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            return visitSelective(positions, wantedBlockIds, consumer);
        }

        private SelectiveChunkStreamStats visitSelective(
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            selectiveCalls++;
            lastWantedBlockIds = wantedBlockIds.clone();
            lastPositions = List.copyOf(positions);
            if (contains(wantedBlockIds, fakeBlockId)) {
                int[] blocks = new int[]{fakeBlockId};
                consumer.accept(new ParsedChunk(
                        new ChunkCoordinate(2, 0, 2),
                        5,
                        1,
                        1,
                        1,
                        blocks
                ));
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    1,
                    registry.containsKey(1) ? 1 : 0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    1
            );
        }

        private boolean contains(int[] values, int wanted) {
            for (int value : values) {
                if (value == wanted) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> null
            );
        }
    }
}
