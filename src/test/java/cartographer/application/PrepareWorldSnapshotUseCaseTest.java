package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.RenderDataCacheStore;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareWorldSnapshotUseCaseTest {

    @TempDir
    Path root;

    @Test
    void secondRunReusesCompleteTerrainAndSurfaceWithoutSourceChunkReads()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3, 4});

        TestReader reader = new TestReader(List.of(
                mapChunk(0, 0),
                mapChunk(1, 0)
        ));
        WorldMetadataReader metadataReader = new TestMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(root.resolve("cache"));
        PrepareWorldSnapshotUseCase useCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        sessionFactory,
                        cacheStore,
                        new WorldIndexBatchPlanner(16)
                );
        PrepareWorldSnapshotRequest request =
                new PrepareWorldSnapshotRequest(save);

        PrepareWorldSnapshotResult first =
                useCase.execute(request, ProgressReporter.NONE);

        assertTrue(first.complete());
        assertEquals(2, first.observedMapChunks());
        assertEquals(2, first.terrainPublished());
        assertEquals(2, first.surfacePublished());
        assertEquals(1, reader.observedScans.get());
        assertEquals(0, reader.exactMapChunkReads.get());
        assertEquals(1, reader.surfaceReads.get());

        PrepareWorldSnapshotResult second =
                useCase.execute(request, ProgressReporter.NONE);

        assertTrue(second.complete());
        assertEquals(2, second.observedMapChunks());
        assertEquals(2, second.terrainHits());
        assertEquals(0, second.terrainPublished());
        assertEquals(2, second.surfaceHits());
        assertEquals(0, second.surfacePublished());

        assertEquals(
                1,
                reader.observedScans.get(),
                "completed revision catalog must avoid another source discovery scan"
        );
        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "valid Terrain coverage must avoid repair reads"
        );
        assertEquals(
                1,
                reader.surfaceReads.get(),
                "valid Surface coverage must avoid another server-chunk traversal"
        );

        PrepareMapDataUseCase renderUseCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory,
                Optional.of(cacheStore)
        );
        renderUseCase.execute(
                new PrepareMapDataRequest(
                        save,
                        31,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                        Optional.of(new WorldPosition(32, 0, 16)),
                        true
                ),
                ProgressReporter.NONE
        );

        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "prepared Terrain coverage must avoid source mapchunk lookups"
        );
        assertEquals(
                1,
                reader.surfaceReads.get(),
                "prepared Surface coverage must avoid source server-chunk reads"
        );

        // Move the Surface request into an observed-gap column. A complete
        // mapchunk catalog may skip the Terrain lookup, but it must not invent
        // the absence of server chunks. The first request therefore performs
        // the established authoritative Surface fallback and publishes the
        // resulting complete tile.
        PrepareMapDataRequest outsidePreparedSurface =
                new PrepareMapDataRequest(
                        save,
                        31,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                        Optional.of(new WorldPosition(64, 0, 16)),
                        true
                );
        renderUseCase.execute(
                outsidePreparedSurface,
                ProgressReporter.NONE
        );

        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "complete catalog may skip known-unobserved Terrain mapchunks"
        );
        assertEquals(
                2,
                reader.surfaceReads.get(),
                "Surface outside prepared coverage must retain authoritative fallback"
        );

        // Once that fallback tile has been published for the same revision,
        // the identical render is snapshot-backed as well.
        renderUseCase.execute(
                outsidePreparedSurface,
                ProgressReporter.NONE
        );
        assertEquals(
                2,
                reader.surfaceReads.get(),
                "a lazily published complete Surface tile must be reusable"
        );
    }

    private static MapChunk mapChunk(int x, int z) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, 0);
        return new MapChunk(
                new MapChunkCoordinate(x, z),
                heights,
                heights
        );
    }

    private static final class TestReader extends VcdbsReader {
        private final List<MapChunk> mapChunks;
        private final AtomicInteger observedScans = new AtomicInteger();
        private final AtomicInteger exactMapChunkReads = new AtomicInteger();
        private final AtomicInteger surfaceReads = new AtomicInteger();

        private TestReader(List<MapChunk> mapChunks) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
            this.mapChunks = List.copyOf(mapChunks);
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(16, 0, 16);
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    0, new BlockInfo(0, "game:air"),
                    1, new BlockInfo(1, "game:rock-granite")
            );
        }

        @Override
        public MapChunkStreamStats forEachObservedMapChunk(
                SaveSession session,
                ReadDiagnostics diagnostics,
                Consumer<MapChunkCoordinate> observedCoordinateConsumer,
                Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            observedScans.incrementAndGet();
            mapChunks.forEach(mapChunk -> {
                observedCoordinateConsumer.accept(mapChunk.coordinate());
                diagnostics.recordParsed();
                consumer.accept(mapChunk);
            });
            return new MapChunkStreamStats(
                    mapChunks.size(),
                    mapChunks.isEmpty() ? 0 : 1,
                    mapChunks.size(),
                    mapChunks.size(),
                    0,
                    0
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            exactMapChunkReads.incrementAndGet();
            List<MapChunkCoordinate> requested = List.copyOf(coordinates);
            List<MapChunk> matching = mapChunks.stream()
                    .filter(mapChunk -> requested.contains(mapChunk.coordinate()))
                    .toList();
            matching.forEach(mapChunk -> {
                diagnostics.recordParsed();
                consumer.accept(mapChunk);
            });
            return new MapChunkStreamStats(
                    requested.size(),
                    requested.isEmpty() ? 0 : 1,
                    matching.size(),
                    matching.size(),
                    0,
                    0
            );
        }

        @Override
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            surfaceReads.incrementAndGet();
            for (ChunkPosition position : positions) {
                ParsedChunk chunk = solidChunk(position);
                diagnostics.recordParsed();
                consumer.accept(chunk);
            }
            return new ChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    positions.size(),
                    positions.size(),
                    0,
                    0
            );
        }

        private ParsedChunk solidChunk(ChunkPosition position) {
            int cells = ChunkCoordinate.SIZE_BLOCKS
                    * ChunkCoordinate.SIZE_BLOCKS
                    * ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[cells];
            int[] liquids = new int[cells];
            Arrays.fill(blocks, 1);
            return new ParsedChunk(
                    new ChunkCoordinate(
                            position.x(),
                            position.y(),
                            position.z()
                    ),
                    position.y() * ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    blocks,
                    liquids,
                    2
            );
        }
    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        private TestMetadataReader() {
            super(null, null);
        }

        @Override
        protected WorldMetadata read(
                Connection connection,
                ProgressReporter progress
        ) {
            return new WorldMetadata(96, 64, 32);
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return false;
                        }
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                        return null;
                    }
            );
        }
    }
}
