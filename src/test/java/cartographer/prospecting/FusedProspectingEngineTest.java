package cartographer.prospecting;

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
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FusedProspectingEngineTest {
    @Test
    void scansOneDecodedStreamForOverlappingResources() {
        CountingReader reader = new CountingReader();
        FusedProspectingResult result = new FusedProspectingEngine(reader, new TestMetadataReader())
                .analyze(Path.of("fixture.vcdbs"), new WorldPosition(16, 0, 16), 16,
                        java.util.List.of("copper", "native"));

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
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return Map.of(
                    7, new BlockInfo(7, "game:rock-granite"),
                    9, new BlockInfo(9, "game:ore-copper-native")
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath, java.util.Collection<ChunkPosition> positions, int[] wantedBlockIds,
                ReadDiagnostics diagnostics, Consumer<SelectiveChunkVisit> consumer) {
            calls.incrementAndGet();
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            java.util.Arrays.fill(blocks, 9);
            for (ChunkPosition position : positions) {
                visits++;
                decodedChunks++;
                consumer.accept(SelectiveChunkVisit.decoded(position, new ParsedChunk(
                        new ChunkCoordinate(position.x(), position.y(), position.z()), 0,
                        size, size, size, blocks)));
            }
            return new SelectiveChunkStreamStats(positions.size(), 1, positions.size(),
                    positions.size(), 0, positions.size(), 0, positions.size());
        }
    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        @Override
        public WorldMetadata read(Path savePath) {
            return new WorldMetadata(64, 64, 64);
        }
    }
}
