package cartographer.application;

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
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadSurfaceMapUseCaseTest {
    @Test
    void readsCompactSurfaceThroughCallbacksWithoutBatchChunkRead() {
        FakeReader reader = new FakeReader();
        ReadSurfaceMapResult result = new ReadSurfaceMapUseCase(
                reader, new FixedMetadataReader()).execute(
                new ReadSurfaceMapRequest(Path.of("fixture.vcdbs"),
                        new WorldPosition(1.6, 0, 1.6), 2, true, true));

        assertEquals(2, result.surface().map().layout().centerWorldX());
        assertEquals(2, result.surface().map().layout().centerWorldZ());
        assertTrue(result.surface().map().isResolved(1, 1));
        assertEquals(1, result.surface().chunksScanned());
        assertTrue(result.surface().columnsScanned() > 0);
        assertEquals(0, result.surface().emptyColumns());
        assertEquals(0, result.surface().liquidUnavailableColumns());
        assertEquals(0, reader.batchReadCalls);
        assertEquals(1, reader.adaptiveReadCalls);
    }

    @Test
    void recordsOneLiquidDecodeFailureWhenTheChunkIsSeenInBothPhases() {
        FakeReader reader = new FakeReader();
        reader.liquidAvailable = false;
        ReadSurfaceMapResult result = new ReadSurfaceMapUseCase(
                reader, new FixedMetadataReader()).execute(
                new ReadSurfaceMapRequest(Path.of("fixture.vcdbs"),
                        new WorldPosition(1, 0, 1), 2, true, true));

        assertEquals(1, result.chunkDiagnostics().liquidDecodeFailures());
        assertEquals(2, reader.adaptiveReadCalls);
    }

    @Test
    void forwardsIgnoreFoliageToCompactSurfaceSession() {
        FakeReader ignoredReader = new FakeReader();
        ignoredReader.blockId = 2;
        ReadSurfaceMapResult ignored = read(ignoredReader, true);

        FakeReader includedReader = new FakeReader();
        includedReader.blockId = 2;
        ReadSurfaceMapResult included = read(includedReader, false);

        assertEquals(0, resolvedCells(ignored));
        assertTrue(resolvedCells(included) > 0);
        assertTrue(ignoredReader.adaptiveReadCalls > 1);
        assertEquals(1, includedReader.adaptiveReadCalls);
    }

    private ReadSurfaceMapResult read(FakeReader reader, boolean ignoreFoliage) {
        return new ReadSurfaceMapUseCase(reader, new FixedMetadataReader()).execute(
                new ReadSurfaceMapRequest(Path.of("fixture.vcdbs"),
                        new WorldPosition(1, 0, 1), 2, ignoreFoliage, true));
    }

    private int resolvedCells(ReadSurfaceMapResult result) {
        int[] count = {0};
        result.surface().map().forEachResolvedCell(
                (x, z, y, blockId, liquidId, surfaceClass) -> count[0]++);
        return count[0];
    }

    private static final class FixedMetadataReader extends WorldMetadataReader {
        @Override
        public WorldMetadata read(Path savePath, ProgressReporter progress) {
            return new WorldMetadata(64, 64, 64);
        }
    }

    private static final class FakeReader extends VcdbsReader {
        private int batchReadCalls;
        private int adaptiveReadCalls;
        private boolean liquidAvailable = true;
        private int blockId = 1;

        private FakeReader() {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath, ProgressReporter progress) {
            return Map.of(0, new BlockInfo(0, "air"),
                    1, new BlockInfo(1, "game:soil-medium"),
                    2, new BlockInfo(2, "game:fern-leaf"));
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath, Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics, Consumer<MapChunk> consumer,
                ProgressReporter progress) {
            consumer.accept(new MapChunk(new MapChunkCoordinate(0, 0),
                    filledHeights(1), new int[0]));
            return new MapChunkStreamStats(coordinates.size(), 1, 1, 1, 0, 0);
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                Path savePath, Collection<cartographer.model.ChunkPosition> positions,
                ReadDiagnostics diagnostics, Consumer<ParsedChunk> consumer,
                ProgressReporter progress) {
            adaptiveReadCalls++;
            consumer.accept(liquidAvailable ? filledChunk() : unavailableChunk());
            return new ChunkStreamStats(positions.size(), 1, 1, 1, 0, 0);
        }

        private ParsedChunk unavailableChunk() {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            return new ParsedChunk(new ChunkCoordinate(0, 0, 0), 0,
                    size, size, size, new int[size * size * size], null,
                    0, false, "liquid decode failed");
        }

        @Override
        public java.util.List<ParsedChunk> readChunksAround(
                Path savePath, cartographer.model.WorldPosition center, int radiusBlocks,
                ReadDiagnostics diagnostics, ProgressReporter progress) {
            batchReadCalls++;
            return java.util.List.of();
        }

        private ParsedChunk filledChunk() {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            Arrays.fill(blocks, blockId);
            return new ParsedChunk(new ChunkCoordinate(0, 0, 0), 0,
                    size, size, size, blocks, new int[blocks.length], 0, true, "");
        }

        private static int[] filledHeights(int value) {
            int[] result = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(result, value);
            return result;
        }
    }
}
