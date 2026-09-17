package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceStreamingSessionTest {
    private static final WorldMetadata WORLD = new WorldMetadata(64, 256, 64);
    private static final Map<Integer, BlockInfo> REGISTRY = Map.of(
            0, new BlockInfo(0, "air"),
            1, new BlockInfo(1, "game:soil-medium"),
            2, new BlockInfo(2, "game:rock-granite")
    );

    @Test
    void lowerFallbackYReplacesEarlierFastObservationWhenMapchunkPromotes() {
        SurfaceStreamingSession session = session(100);
        session.acceptMapChunk(new MapChunk(
                new MapChunkCoordinate(0, 0), filled(100), new int[0]));
        session.finishPlanning();
        session.acceptFastChunk(airChunk(3));
        assertEquals(
                List.of(new MapChunkCoordinate(0, 0)),
                session.fallbackMapChunks()
        );
        session.acceptFallbackChunk(fallbackChunk(2, 95, 1));

        SurfaceRainHeightScanResult result = session.finish();

        assertTrue(result.fallbackMapChunks().contains(new MapChunkCoordinate(0, 0)));
        assertTrue(result.surface().isResolved(1, 1));
        assertEquals(95, result.surface().surfaceYAt(1, 1));
    }

    @Test
    void fastMissingLiquidPromotesBeforeFallbackAndDoesNotLeakFinalDiagnostic() {
        SurfaceStreamingSession session = session(1);
        session.acceptMapChunk(new MapChunk(
                new MapChunkCoordinate(0, 0), filled(1), new int[0]));
        session.finishPlanning();
        session.acceptFastChunk(unavailableChunk(0));

        assertTrue(session.fallbackMapChunks().contains(new MapChunkCoordinate(0, 0)));
        session.acceptFallbackChunk(chunkFilled(0, 1, 1));

        SurfaceRainHeightScanResult result = session.finish();

        assertEquals(0, result.diagnostics().liquidUnavailableColumns());
        assertFalse(result.surface().isLiquidUnavailable(1, 1));
        assertTrue(result.surface().isConsidered(1, 1));
        assertTrue(result.surface().isResolved(1, 1));
    }

    @Test
    void fallbackMissingLiquidRemainsVisibleInFinalCompactState() {
        SurfaceStreamingSession session = SurfaceStreamingSession.begin(
                WORLD, 1, 1, 1,
                List.of(new MapChunkCoordinate(0, 0)), REGISTRY, true, true);
        session.finishPlanning();
        session.acceptFallbackChunk(unavailableChunk(0));

        SurfaceRainHeightScanResult result = session.finish();

        assertTrue(result.surface().isConsidered(1, 1));
        assertTrue(result.surface().isLiquidUnavailable(1, 1));
        assertEquals(ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS,
                result.diagnostics().liquidUnavailableColumns());
    }

    @Test
    void missingRequestedServerChunkPromotesBeforeFallbackScheduling() {
        SurfaceStreamingSession session = session(1);
        session.acceptMapChunk(new MapChunk(
                new MapChunkCoordinate(0, 0), filled(1), new int[0]));
        session.finishPlanning();

        assertTrue(session.fallbackMapChunks().contains(new MapChunkCoordinate(0, 0)));
    }

    @Test
    void fallbackDiagnosticsIncludeColumnsOutsidePartialCircleAndDeduplicateVerticalChunks() {
        SurfaceStreamingSession session = SurfaceStreamingSession.begin(
                WORLD, 1, 1, 1,
                List.of(new MapChunkCoordinate(0, 0)), REGISTRY, true, true);
        session.finishPlanning();
        session.acceptFallbackChunk(chunkFilled(0, 1, 1));
        session.acceptFallbackChunk(chunkFilled(1, 33, 1));

        SurfaceRainHeightScanResult result = session.finish();

        assertEquals(ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS,
                result.diagnostics().columnsScanned());
        assertEquals(0, result.diagnostics().emptyColumns());
    }

    @Test
    void repeatedMissingLiquidVerticalChunksCountEachColumnOnce() {
        SurfaceStreamingSession session = SurfaceStreamingSession.begin(
                WORLD, 1, 1, 1,
                List.of(new MapChunkCoordinate(0, 0)), REGISTRY, true, true);
        session.finishPlanning();
        session.acceptFallbackChunk(unavailableChunk(0));
        session.acceptFallbackChunk(unavailableChunk(1));

        SurfaceRainHeightScanResult result = session.finish();

        assertEquals(ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS,
                result.diagnostics().liquidUnavailableColumns());
    }

    @Test
    void fallbackSelectsHighestAcrossArbitraryVerticalArrivalOrder() {
        SurfaceStreamingSession first = session(0);
        SurfaceStreamingSession second = session(0);
        first.finishPlanning();
        second.finishPlanning();
        ParsedChunk lower = chunkFilled(0, 20, 1);
        ParsedChunk upper = chunkFilled(1, 50, 2);
        first.acceptFallbackChunk(lower);
        first.acceptFallbackChunk(upper);
        second.acceptFallbackChunk(upper);
        second.acceptFallbackChunk(lower);

        assertEquals(
                SurfaceSemanticOracle.fingerprint(first.finish().surface()),
                SurfaceSemanticOracle.fingerprint(second.finish().surface())
        );
    }

    @Test
    void sessionConsumesCallbacksWithoutExposingDecodedChunkOwnership() {
        SurfaceStreamingSession session = session(1);
        session.acceptMapChunk(new MapChunk(
                new MapChunkCoordinate(0, 0), filled(1), new int[0]));
        session.finishPlanning();
        ParsedChunk chunk = chunkFilled(0, 1, 1);
        session.acceptFastChunk(chunk);
        SurfaceRainHeightScanResult result = session.finish();

        assertTrue(result.surface().isResolved(1, 1));
        assertFalse(result.surface().isLiquidUnavailable(1, 1));
    }

    private SurfaceStreamingSession session(int rainHeight) {
        return SurfaceStreamingSession.begin(
                WORLD, 1, 1, 2,
                List.of(new MapChunkCoordinate(0, 0)),
                REGISTRY, true, true
        );
    }

    private ParsedChunk chunkFilled(int sectionY, int worldY, int blockId) {
        int[] blocks = new int[32 * 32 * 32];
        Arrays.fill(blocks, blockId);
        return new ParsedChunk(
                new ChunkCoordinate(0, sectionY, 0),
                sectionY * ChunkCoordinate.SIZE_BLOCKS,
                32, 32, 32, blocks
        );
    }

    private ParsedChunk airChunk(int sectionY) {
        return new ParsedChunk(
                new ChunkCoordinate(0, sectionY, 0),
                sectionY * ChunkCoordinate.SIZE_BLOCKS,
                32, 32, 32, new int[32 * 32 * 32]
        );
    }

    private ParsedChunk fallbackChunk(int sectionY, int worldY, int blockId) {
        int[] blocks = new int[32 * 32 * 32];
        int localY = worldY - sectionY * ChunkCoordinate.SIZE_BLOCKS;
        for (int localZ = 0; localZ < 32; localZ++) {
            for (int localX = 0; localX < 32; localX++) {
                blocks[(localY * 32 + localZ) * 32 + localX] = blockId;
            }
        }
        return new ParsedChunk(
                new ChunkCoordinate(0, sectionY, 0),
                sectionY * ChunkCoordinate.SIZE_BLOCKS,
                32, 32, 32, blocks
        );
    }

    private ParsedChunk unavailableChunk(int sectionY) {
        return new ParsedChunk(
                new ChunkCoordinate(0, sectionY, 0),
                sectionY * ChunkCoordinate.SIZE_BLOCKS,
                32, 32, 32,
                new int[32 * 32 * 32],
                null,
                0,
                false,
                "liquid layer missing"
        );
    }

    private static int[] filled(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, value);
        return heights;
    }
}
