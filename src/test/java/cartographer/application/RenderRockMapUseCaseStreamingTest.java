package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderRockMapUseCaseStreamingTest {
    @Test
    void preservesResultContractForUpperAndAtYStreamingModes() {
        StreamingReader reader = new StreamingReader();
        RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                reader, new MetadataReader(), new RockMapRenderer());
        WorldPosition center = new WorldPosition(16, 0, 16);

        RenderRockMapResult upper = useCase.execute(new RenderRockMapRequest(
                Path.of("world.vcdbs"), cartographer.geology.rock.RockMapMode.UPPER_ROCK,
                1, java.util.Optional.of(center), java.util.OptionalInt.empty(),
                java.util.OptionalInt.of(0), java.util.OptionalInt.of(64)));
        RenderRockMapResult atY = useCase.execute(new RenderRockMapRequest(
                Path.of("world.vcdbs"), cartographer.geology.rock.RockMapMode.AT_Y,
                1, java.util.Optional.of(center), java.util.OptionalInt.of(31),
                java.util.OptionalInt.empty(), java.util.OptionalInt.empty()));

        assertEquals(5, upper.map().observedCount());
        assertEquals(5, atY.map().observedCount());
        assertEquals(0, upper.map().unavailableCount());
        assertEquals(0, atY.map().unavailableCount());
        assertEquals(cartographer.geology.rock.RockMapMode.UPPER_ROCK, upper.map().mode());
        assertEquals(cartographer.geology.rock.RockMapMode.AT_Y, atY.map().mode());
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
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return Map.of(1, new BlockInfo(1, "game:rock-granite"));
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath, java.util.Collection<ChunkPosition> positions, int[] wantedBlockIds,
                ReadDiagnostics diagnostics, Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress) {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            for (ChunkPosition position : positions) {
                int[] blocks = new int[size * size * size];
                java.util.Arrays.fill(blocks, 1);
                consumer.accept(SelectiveChunkVisit.decoded(position, new ParsedChunk(
                        new ChunkCoordinate(position.x(), position.y(), position.z()),
                        position.y() * size, size, size, size, blocks)));
            }
            return stats;
        }
    }

    private static final class MetadataReader extends WorldMetadataReader {
        @Override
        public WorldMetadata read(Path savePath) {
            return new WorldMetadata(64, 64, 64);
        }
    }
}
