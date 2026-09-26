package cartographer.application;

import cartographer.testing.TestConnections;
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
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderRockMapUseCaseStreamingTest {
    @Test
    void preservesResultContractForUpperAndAtYStreamingModes() {
        StreamingReader reader = new StreamingReader();
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader, new RockMapRenderer(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, new MetadataReader()));
        WorldPosition center = new WorldPosition(16, 0, 16);
        Path savePath = Path.of("world.vcdbs");

        RenderRockMapResult upper = useCase.execute(new RenderRockMapRequest(
                savePath, cartographer.geology.rock.RockMapMode.UPPER_ROCK,
                1, java.util.Optional.of(center), java.util.OptionalInt.empty(),
                java.util.OptionalInt.of(0), java.util.OptionalInt.of(64)));
        RenderRockMapResult atY = useCase.execute(new RenderRockMapRequest(
                savePath, cartographer.geology.rock.RockMapMode.AT_Y,
                1, java.util.Optional.of(center), java.util.OptionalInt.of(31),
                java.util.OptionalInt.empty(), java.util.OptionalInt.empty()));

        assertEquals(5, upper.retainedMap().orElseThrow().observedCount());
        assertEquals(5, atY.retainedMap().orElseThrow().observedCount());
        assertEquals(0, upper.retainedMap().orElseThrow().unavailableCount());
        assertEquals(0, atY.retainedMap().orElseThrow().unavailableCount());
        assertEquals(cartographer.geology.rock.RockMapMode.UPPER_ROCK, upper.retainedMap().orElseThrow().mode());
        assertEquals(cartographer.geology.rock.RockMapMode.AT_Y, atY.retainedMap().orElseThrow().mode());
        assertEquals(center, upper.center());
        assertEquals(center, atY.center());
        assertEquals(0, upper.minY());
        assertEquals(64, upper.maxYExclusive());
        assertEquals(31, atY.minY());
        assertEquals(32, atY.maxYExclusive());
        assertEquals(upper.catalog().rocks(), atY.catalog().rocks());
        assertSame(reader.stats, upper.chunkStats());
        assertSame(reader.stats, atY.chunkStats());
        assertEquals(5, upper.rendered().observedCount());
        assertEquals(5, atY.rendered().observedCount());
    }

    private static final class StreamingReader extends VcdbsReader {
        private final SelectiveChunkStreamStats stats = new SelectiveChunkStreamStats(
                1, 1, 1, 1, 0, 1, 0, 1);

        private StreamingReader() {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            return Map.of(1, new BlockInfo(1, "game:rock-granite"));
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session, java.util.Collection<ChunkPosition> positions, int[] wantedBlockIds,
                ReadDiagnostics diagnostics, Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress) {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            for (ChunkPosition position : positions) {
                int[] blocks = new int[size * size * size];
                java.util.Arrays.fill(blocks, 1);
                consumer.accept(SelectiveChunkVisit.decoded(position, cartographer.model.ParsedChunkFixtures.create(
                        new ChunkCoordinate(position.x(), position.y(), position.z()),
                        position.y() * size, size, size, size, blocks)));
            }
            return stats;
        }
    }

    private static final class MetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(Connection connection) {
            return new WorldMetadata(64, 64, 64);
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return TestConnections.noOp();
        }
    }
}
