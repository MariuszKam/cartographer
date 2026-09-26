package cartographer.application;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.spatial.OreChunkPositionPlanner;
import cartographer.progress.ProgressReporter;
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
import cartographer.cache.RenderDataCacheRevision;
import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.TerrainTileStore;
import cartographer.cache.SurfaceTileStore;
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
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cartographer.testing.ImageAssertions.assertImageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class RenderActualOreMapUseCaseTestSupport {

    @TempDir
    Path temporaryDirectory;

    static void assertParity(
            RenderActualOreMapResult expected,
            RenderActualOreMapResult actual
    ) {
        assertEquals(expected.geometry(), actual.geometry());
        assertEquals(expected.surface().columnsScanned(), actual.surface().columnsScanned());
        assertEquals(expected.surface().emptyColumns(), actual.surface().emptyColumns());
        assertEquals(
                expected.surface().liquidUnavailableColumns(),
                actual.surface().liquidUnavailableColumns()
        );
        assertEquals(expected.surface().waterColumns(), actual.surface().waterColumns());
        assertEquals(
                expected.surface().unknownSurfaceBlocks(),
                actual.surface().unknownSurfaceBlocks()
        );
        assertEquals(
                expected.surface().topUnknownSurfaceBlockCodes(Integer.MAX_VALUE),
                actual.surface().topUnknownSurfaceBlockCodes(Integer.MAX_VALUE)
        );
        assertEquals(
                expected.surface().distinctSurfaceBlockCodes(Integer.MAX_VALUE),
                actual.surface().distinctSurfaceBlockCodes(Integer.MAX_VALUE)
        );
        assertImageEquals(expected.image(), actual.image());
    }

    RenderActualOreMapResult execute(FakeReader reader) {
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

    boolean hasSurfaceCode(
            RenderActualOreMapResult result,
            String code
    ) {
        return result.surface()
                .distinctSurfaceBlockCodes(Integer.MAX_VALUE)
                .contains(code);
    }

    boolean hasSurfaceXLessThan(
            RenderActualOreMapResult result
    ) {
        var renderData = result.preparedMapData()
                .orElseThrow()
                .surface()
                .renderData();
        for (int y = 0; y < renderData.rasterSize(); y++) {
            for (int x = 0; x < renderData.rasterSize(); x++) {
                if (cartographer.render.SurfaceRenderDataTestAccess.hasSurfaceAt(renderData, x, y)
                        && cartographer.render.SurfaceRenderDataTestAccess.surfaceWorldXAt(renderData, x, y) < 32) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean hasSurfaceXAtLeast(
            RenderActualOreMapResult result
    ) {
        var renderData = result.preparedMapData()
                .orElseThrow()
                .surface()
                .renderData();
        for (int y = 0; y < renderData.rasterSize(); y++) {
            for (int x = 0; x < renderData.rasterSize(); x++) {
                if (cartographer.render.SurfaceRenderDataTestAccess.hasSurfaceAt(renderData, x, y)
                        && cartographer.render.SurfaceRenderDataTestAccess.surfaceWorldXAt(renderData, x, y) >= 32) {
                    return true;
                }
            }
        }
        return false;
    }

    RenderActualOreMapResult execute(
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

    RenderActualOreMapResult execute(
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

    RenderActualOreMapResult execute(
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

    RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader,
                new HomeStore(homePath),
                new MarkerStore(markerPath),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader),
                Optional.empty()
        );
    }

    RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath,
            RenderDataCacheStore renderDataCacheStore
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader,
                new HomeStore(homePath),
                new MarkerStore(markerPath),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader),
                Optional.of(renderDataCacheStore)
        );
    }

    RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath,
            TestConnectionFactory connections
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader, new HomeStore(homePath), new MarkerStore(markerPath),
                new MapRenderer(), new UserMarkerRenderer(),
                new ActualOreOverlayPainter(), new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(connections, reader, metadataReader),
                Optional.empty()
        );
    }

    static void corruptSurfaceRow(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) throws Exception {
        Path database = new SurfaceTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE surface_tile SET payload = X'00'");
        }
    }

    static void corruptTerrainRow(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new TerrainTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE terrain_tile SET payload = X'00' "
                    + "WHERE mapchunk_x = " + coordinate.x()
                    + " AND mapchunk_z = " + coordinate.z());
        }
    }

    static byte[] terrainPayload(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new TerrainTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT payload FROM terrain_tile WHERE mapchunk_x = "
                             + coordinate.x() + " AND mapchunk_z = " + coordinate.z())) {
            assertTrue(resultSet.next());
            return resultSet.getBytes("payload");
        }
    }

    static byte[] surfacePayload(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new SurfaceTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT payload FROM surface_tile WHERE mapchunk_x = "
                             + coordinate.x() + " AND mapchunk_z = " + coordinate.z())) {
            assertTrue(resultSet.next());
            return resultSet.getBytes("payload");
        }
    }

    static String completeManifest(
            RenderDataCacheRevision revision,
            String schemaVersion,
            String compatibilityVersion
    ) {
        String encodedPath = Base64.getEncoder().encodeToString(
                revision.identity().normalizedSavePath().toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        return "schemaVersion=" + schemaVersion + "\n"
                + "normalizedSavePathBase64=" + encodedPath + "\n"
                + "namespaceHash=" + revision.identity().namespaceHash() + "\n"
                + "saveSize=" + revision.saveSize() + "\n"
                + "saveModifiedMillis=" + revision.saveModifiedMillis() + "\n"
                + "compatibilityVersion=" + compatibilityVersion + "\n";
    }

    static void assertExplicitCacheArtifactsContained(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            Path savePath
    ) throws Exception {
        Path root = cacheStore.cacheRoot().toAbsolutePath().normalize();
        Path sourceDirectory = savePath.toAbsolutePath().normalize().getParent();
        List<Path> artifacts = List.of(
                cacheStore.manifestPath(revision),
                new TerrainTileStore(cacheStore, revision).databasePath(),
                new SurfaceTileStore(cacheStore, revision).databasePath()
        );
        for (Path artifact : artifacts) {
            Path normalized = artifact.toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(normalized),
                    "expected render-data cache artifact is missing: " + normalized);
            assertTrue(normalized.startsWith(root),
                    "render-data cache artifact escaped cache root: " + normalized);
            assertFalse(normalized.startsWith(sourceDirectory),
                    "render-data cache artifact was placed beside the source save: " + normalized);
        }
        for (String name : List.of("manifest.properties", "terrain-cache.sqlite",
                "surface-cache.sqlite")) {
            assertFalse(Files.exists(savePath.resolveSibling(name)),
                    "cache artifact appeared beside source save: " + name);
        }
        Path revisionDirectory = cacheStore.manifestPath(revision).getParent();
        try (var paths = Files.list(revisionDirectory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".manifest-")),
                    "temporary manifest publication file survived");
        }
    }

    FakeReader surfaceReader(boolean liquidAvailable) {
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

    FakeReader fertilityReader() {
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

    Map<Integer, BlockInfo> fireClayRegistry() {
        return Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:fire-clay-blue")
        );
    }

    ParsedChunk surfaceChunk(
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
        return cartographer.model.ParsedChunkFixtures.create(coordinate, 0, size, size, size, blocks, liquids,
                0, liquidAvailable, liquidDecodeError);
    }

    static int[] filledHeights() {
        return filledHeights(5);
    }

    static int[] filledHeights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, value);
        return heights;
    }

    FakeReader edgeFallbackReader() {
        FakeReader reader = new FakeReader(fireClayRegistry());
        reader.chunks.put(
                new ChunkPosition(1, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(1, 0, 0), false)
        );
        return reader;
    }

    static final class FakeReader extends VcdbsReader {
        final Map<Integer, BlockInfo> registry;
        final Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();
        final List<List<MapChunkCoordinate>> directMapChunkRequests = new ArrayList<>();
        final List<List<ChunkPosition>> exactRequests = new ArrayList<>();
        int selectiveCalls;
        int adaptiveSelectiveCalls;
        int directMapChunkCalls;
        int exactChunkCalls;
        int adaptiveExactChunkCalls;
        int registryCalls;
        int sessionMapRegionCalls;
        final int fakeBlockId;
        int[] lastWantedBlockIds = new int[0];
        List<cartographer.model.ChunkPosition> lastPositions = List.of();

        FakeReader(Map<Integer, BlockInfo> registry) {
            this(registry, 1);
        }

        FakeReader(Map<Integer, BlockInfo> registry, int fakeBlockId) {
            super(
                    new cartographer.parser.PlayerDataParser(),
                    new cartographer.parser.MapChunkParser(),
                    new cartographer.parser.ChunkParser(),
                    new cartographer.parser.RegistryParser()
            );
            this.registry = registry;
            this.fakeBlockId = fakeBlockId;
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(64, 64, 64);
        }

        MapChunkStreamStats visitMapChunks(
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
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveExactChunkCalls++;
            return visitChunks(
                    positions,
                    consumer
            );
        }

        ChunkStreamStats visitChunks(
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
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            registryCalls++;
            return registry;
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

        SelectiveChunkStreamStats visitSelective(
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            selectiveCalls++;
            lastWantedBlockIds = wantedBlockIds.clone();
            lastPositions = List.copyOf(positions);
            if (contains(wantedBlockIds, fakeBlockId)) {
                int[] blocks = new int[]{fakeBlockId};
                consumer.accept(cartographer.model.ParsedChunkFixtures.create(
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

        boolean contains(int[] values, int wanted) {
            for (int value : values) {
                if (value == wanted) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class TestConnectionFactory extends SqliteSaveConnection {
        int opened;
        int closed;

        @Override
        public Connection openReadOnly(Path savePath) {
            opened++;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")
                                && method.getParameterCount() == 0) {
                            closed++;
                        }
                        return null;
                    }
            );
        }

        int opened() {
            return opened;
        }

        int closed() {
            return closed;
        }
    }
}
