package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.RenderStyle;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceMapScanResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareMapDataUseCaseSurfaceAnalysisTest {
    @Test
    void readsExactSurfaceAnalysisThroughCompactCallbacks() {
        FakeReader reader = new FakeReader();

        PreparedMapData prepared = read(
                reader,
                new WorldPosition(1.6, 0, 1.6),
                true
        );
        SurfaceMapScanResult result =
                prepared.surface().requireAnalysis();

        assertEquals(2, result.map().layout().centerWorldX());
        assertEquals(2, result.map().layout().centerWorldZ());
        assertTrue(result.map().isResolved(1, 1));
        assertEquals(1, result.chunksScanned());
        assertTrue(result.columnsScanned() > 0);
        assertEquals(0, result.emptyColumns());
        assertEquals(0, result.liquidUnavailableColumns());
        assertEquals(1, reader.adaptiveReadCalls);
    }

    @Test
    void recordsOneLiquidDecodeFailureWhenChunkIsSeenInBothPhases() {
        FakeReader reader = new FakeReader();
        reader.liquidAvailable = false;

        PreparedMapData prepared = read(
                reader,
                new WorldPosition(1, 0, 1),
                true
        );

        assertEquals(
                1,
                prepared.chunkDiagnostics().liquidDecodeFailures()
        );
        assertEquals(2, reader.adaptiveReadCalls);
    }

    @Test
    void forwardsIgnoreFoliageToExactSurfaceAnalysis() {
        FakeReader ignoredReader = new FakeReader();
        ignoredReader.blockId = 2;
        SurfaceMapScanResult ignored = read(
                ignoredReader,
                new WorldPosition(1, 0, 1),
                true
        ).surface().requireAnalysis();

        FakeReader includedReader = new FakeReader();
        includedReader.blockId = 2;
        SurfaceMapScanResult included = read(
                includedReader,
                new WorldPosition(1, 0, 1),
                false
        ).surface().requireAnalysis();

        assertEquals(0, resolvedCells(ignored));
        assertTrue(resolvedCells(included) > 0);
        assertTrue(ignoredReader.adaptiveReadCalls > 1);
        assertEquals(1, includedReader.adaptiveReadCalls);
    }

    private PreparedMapData read(
            FakeReader reader,
            WorldPosition center,
            boolean ignoreFoliage
    ) {
        PrepareMapDataUseCase useCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory(reader),
                Optional.empty()
        );
        return useCase.execute(
                new PrepareMapDataRequest(
                        Path.of("fixture.vcdbs"),
                        2,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(),
                        Optional.of(center),
                        SurfaceDataRequirement.ANALYSIS,
                        ignoreFoliage
                ),
                ProgressReporter.NONE
        );
    }

    private SaveSessionFactory sessionFactory(FakeReader reader) {
        return new SaveSessionFactory(
                new FakeSqliteSaveConnection(),
                reader,
                new FixedMetadataReader()
        );
    }

    private int resolvedCells(SurfaceMapScanResult result) {
        int[] count = {0};
        result.map().forEachResolvedCell(
                (x, z, y, blockId, surfaceClass) ->
                        count[0]++
        );
        return count[0];
    }

    private static final class FakeSqliteSaveConnection
            extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "toString" -> "FakeConnection";
                        default -> throw new UnsupportedOperationException(
                                "Unexpected JDBC call: " + method.getName()
                        );
                    }
            );
        }
    }

    private static final class FixedMetadataReader
            extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(
                Connection connection,
                ProgressReporter progress
        ) {
            return new WorldMetadata(64, 64, 64);
        }
    }

    private static final class FakeReader extends VcdbsReader {
        private int adaptiveReadCalls;
        private boolean liquidAvailable = true;
        private int blockId = 1;

        private FakeReader() {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    0, new BlockInfo(0, "air"),
                    1, new BlockInfo(1, "game:soil-medium"),
                    2, new BlockInfo(2, "game:fern-leaf")
            );
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(1, 0, 1);
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            consumer.accept(new MapChunk(
                    new MapChunkCoordinate(0, 0),
                    filledHeights(),
                    filledHeights()
            ));
            return new MapChunkStreamStats(
                    coordinates.size(),
                    1,
                    1,
                    1,
                    0,
                    0
            );
        }

        @Override
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                Collection<cartographer.model.ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveReadCalls++;
            consumer.accept(
                    liquidAvailable
                            ? filledChunk()
                            : unavailableChunk()
            );
            return new ChunkStreamStats(
                    positions.size(),
                    1,
                    1,
                    1,
                    0,
                    0
            );
        }

        private ParsedChunk unavailableChunk() {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            return cartographer.model.ParsedChunkFixtures.create(
                    new ChunkCoordinate(0, 0, 0),
                    0,
                    size,
                    size,
                    size,
                    new int[size * size * size],
                    null,
                    0,
                    false,
                    "liquid decode failed"
            );
        }

        private ParsedChunk filledChunk() {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            Arrays.fill(blocks, blockId);
            return cartographer.model.ParsedChunkFixtures.create(
                    new ChunkCoordinate(0, 0, 0),
                    0,
                    size,
                    size,
                    size,
                    blocks,
                    new int[blocks.length],
                    0,
                    true,
                    ""
            );
        }

        private static int[] filledHeights() {
            int[] result = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(result, 1);
            return result;
        }
    }
}
