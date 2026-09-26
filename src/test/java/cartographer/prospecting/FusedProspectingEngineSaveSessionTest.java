package cartographer.prospecting;

import cartographer.resource.ActualOreObservation;
import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FusedProspectingEngineSaveSessionTest {
    @Test
    void scansOneDecodedStreamForOverlappingResources() {
        CountingReader reader = new CountingReader();
        TestMetadataReader metadataReader = new TestMetadataReader();
        SaveSessionFactory sessions = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        FusedProspectingResult result;
        try (SaveSession session = sessions.open(Path.of("fixture.vcdbs"))) {
            result = new FusedProspectingEngine(reader).analyze(
                    session,
                    new WorldPosition(16, 0, 16),
                    16,
                    java.util.List.of("copper", "native")
            );
        }

        assertEquals(1, reader.calls.get());
        assertEquals(reader.visits, reader.decodedChunks,
                "every emitted decoded chunk is consumed once");
        assertEquals(ActualOreObservation.OBSERVED, result.observation("copper"));
        assertEquals(ActualOreObservation.OBSERVED, result.observation("native"));
    }

    private static final class CountingReader extends VcdbsReader {
        private final AtomicInteger calls = new AtomicInteger();
        private int visits;
        private int decodedChunks;

        private CountingReader() {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            return Map.of(
                    7, new BlockInfo(7, "game:rock-granite"),
                    9, new BlockInfo(9, "game:ore-copper-native")
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session, java.util.Collection<ChunkPosition> positions, int[] wantedBlockIds,
                ReadDiagnostics diagnostics, Consumer<SelectiveChunkVisit> consumer) {
            calls.incrementAndGet();
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            java.util.Arrays.fill(blocks, 9);
            for (ChunkPosition position : positions) {
                visits++;
                decodedChunks++;
                consumer.accept(SelectiveChunkVisit.decoded(position, cartographer.model.ParsedChunkFixtures.create(
                        new ChunkCoordinate(position.x(), position.y(), position.z()), 0,
                        size, size, size, blocks)));
            }
            return new SelectiveChunkStreamStats(positions.size(), 1, positions.size(),
                    positions.size(), 0, positions.size(), 0, positions.size());
        }
    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(Connection connection) {
            return new WorldMetadata(64, 64, 64);
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        if (method.getReturnType() == float.class) return 0.0f;
                        if (method.getReturnType() == double.class) return 0.0d;
                        return null;
                    }
            );
        }
    }
}
