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
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverObservedSurfaceResourcesUseCaseTest {
    @Test
    void suppliesAllCandidateIdsToOneSelectiveScan() {
        FakeReader reader = new FakeReader();
        DiscoverObservedSurfaceResourcesUseCase useCase =
                new DiscoverObservedSurfaceResourcesUseCase(reader, metadataReader());

        var result = useCase.execute(new DiscoverObservedSurfaceResourcesRequest(
                Path.of("world.vcdbs"),
                1,
                java.util.Optional.of(new WorldPosition(16, 0, 16))
        ));

        assertEquals(1, reader.selectiveScanCalls);
        assertArrayEquals(new int[] {1, 2}, reader.lastWantedIds);
        assertEquals(List.of("game:nativecopper"),
                result.observedResources().observedQualifiedResourceKeys());
    }

    @Test
    void zeroSelectiveCallbacksMakePlannedTargetsUnavailable() {
        FakeReader reader = new FakeReader();
        reader.skipSelectiveCallbacks = true;
        DiscoverObservedSurfaceResourcesUseCase useCase =
                new DiscoverObservedSurfaceResourcesUseCase(reader, metadataReader());

        var result = useCase.execute(new DiscoverObservedSurfaceResourcesRequest(
                Path.of("world.vcdbs"), 1,
                java.util.Optional.of(new WorldPosition(16, 0, 16))
        ));

        assertEquals(1, reader.selectiveScanCalls);
        assertTrue(result.scan().unavailablePositions() > 0);
        assertEquals(0, result.scan().observedTargets());
    }

    private WorldMetadataReader metadataReader() {
        return new WorldMetadataReader(null, null) {
            @Override
            public WorldMetadata read(Path savePath) {
                return new WorldMetadata(32, 64, 32);
            }
        };
    }

    private static final class FakeReader extends VcdbsReader {
        private int selectiveScanCalls;
        private int[] lastWantedIds = new int[0];
        private boolean skipSelectiveCallbacks;

        private FakeReader() {
            super(new PlayerDataParser(), new MapChunkParser(),
                    new ChunkParser(), new RegistryParser());
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return Map.of(
                    1, new BlockInfo(1,
                            "game:looseores-nativecopper-granite-free"),
                    2, new BlockInfo(2,
                            "game:loosestones-obsidian-free")
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer
        ) {
            consumer.accept(new MapChunk(
                    new MapChunkCoordinate(0, 0),
                    new int[MapChunk.HEIGHT_VALUE_COUNT],
                    filledHeights(5)
            ));
            return new MapChunkStreamStats(
                    coordinates.size(), 1, 1, 1, 0, 0
            );
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<SelectiveChunkVisit> consumer
        ) {
            selectiveScanCalls++;
            lastWantedIds = Arrays.copyOf(wantedBlockIds, wantedBlockIds.length);
            if (skipSelectiveCallbacks) {
                return new SelectiveChunkStreamStats(
                        positions.size(), 0, 0, 0, 0, 0, 0, 0);
            }
            ParsedChunk chunk = chunkWithBlock(16, 6, 1);
            consumer.accept(SelectiveChunkVisit.decoded(
                    new ChunkPosition(0, 0, 0, 0), chunk
            ));
            return new SelectiveChunkStreamStats(
                    positions.size(), 1, 1, 1, 0, 1, 0, 0
            );
        }

        private int[] filledHeights(int value) {
            int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(heights, value);
            return heights;
        }

        private ParsedChunk chunkWithBlock(int worldX, int worldY, int blockId) {
            int[] blocks = new int[32 * 32 * 32];
            blocks[(worldY * 32 + 16) * 32 + worldX] = blockId;
            return new ParsedChunk(
                    new ChunkCoordinate(0, 0, 0), 0, 32, 32, 32, blocks
            );
        }
    }
}
