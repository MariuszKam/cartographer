package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused characterization of legacy Surface behavior before B migration. */
class SurfaceLegacyCharacterizationTest {
    private static final Map<Integer, BlockInfo> REGISTRY = Map.of(
            0, new BlockInfo(0, "air"),
            1, new BlockInfo(1, "game:soil-medium"),
            2, new BlockInfo(2, "game:leaves-oak"),
            3, new BlockInfo(3, "game:water-still"),
            99, new BlockInfo(99, "othermod:mystery")
    );

    @Test
    void legacyPlannerUsesRoundedCenterEquivalentAtFractionalBoundaries() {
        RainHeightSurfacePlanner.StreamingSession roundedUp = new RainHeightSurfacePlanner()
                .begin(new WorldMetadata(64, 256, 64), 2, 2, 1);
        roundedUp.accept(mapChunk(1));
        RainHeightSurfacePlanner.StreamingSession roundedDown = new RainHeightSurfacePlanner()
                .begin(new WorldMetadata(64, 256, 64), 1, 2, 1);
        roundedDown.accept(mapChunk(1));

        assertEquals(5, roundedUp.finish().targets().size());
        assertEquals(5, roundedDown.finish().targets().size());
    }

    @Test
    void legacyPlannerUsesWholeMapChunkFallbackForMissingOrInvalidRainHeight() {
        int[] invalid = heights(1);
        invalid[0] = 256;
        RainHeightSurfacePlanner.StreamingSession session = new RainHeightSurfacePlanner()
                .begin(new WorldMetadata(64, 256, 64), 1, 1, 8);
        session.accept(new MapChunk(new MapChunkCoordinate(0, 0), new int[0], heights(1)));
        session.accept(new MapChunk(new MapChunkCoordinate(1, 0), invalid, new int[0]));

        RainHeightSurfacePlan plan = session.finish();

        assertTrue(plan.targets().isEmpty());
        assertEquals(List.of(
                new MapChunkCoordinate(0, 0),
                new MapChunkCoordinate(1, 0)
        ), plan.fallbackMapChunks());
    }

    @Test
    void legacyPlannerTreatsNegativeRainHeightAsWholeMapChunkFallback() {
        RainHeightSurfacePlanner.StreamingSession session = new RainHeightSurfacePlanner()
                .begin(new WorldMetadata(32, 256, 32), 1, 1, 8);
        session.accept(new MapChunk(
                new MapChunkCoordinate(0, 0), heights(-1), new int[0]));

        RainHeightSurfacePlan plan = session.finish();

        assertTrue(plan.targets().isEmpty());
        assertEquals(List.of(new MapChunkCoordinate(0, 0)), plan.fallbackMapChunks());
    }

    @Test
    void legacyScannerCharacterizesAirFoliageWaterLiquidAndUnknown() {
        ParsedChunk chunk = new ParsedChunk(
                new ChunkCoordinate(0, 0, 0),
                0, 1, 4, 1,
                new int[]{0, 2, 99, 0},
                new int[]{0, 0, 0, 3},
                0, true, ""
        );
        SurfaceScanResult result = new SurfaceScanner().scan(
                List.of(chunk), REGISTRY, true);

        assertEquals(1, result.blocks().size());
        assertEquals(SurfaceClass.WATER, result.blocks().getFirst().surfaceClass());
        assertEquals(1, result.waterColumns());
    }

    @Test
    void legacyFallbackSelectsHighestAcrossVerticalChunksAndTracksEmptyColumns() {
        ParsedChunk lower = new ParsedChunk(
                new ChunkCoordinate(0, 0, 0), 0, 1, 1, 1,
                new int[]{1});
        ParsedChunk upper = new ParsedChunk(
                new ChunkCoordinate(0, 1, 0), 32, 1, 1, 1,
                new int[]{1});
        SurfaceScanResult result = new SurfaceScanner().scan(
                List.of(upper, lower), REGISTRY, true);

        assertEquals(32, result.blocks().getFirst().y());
        assertEquals(1, result.columnsScanned());
        assertEquals(0, result.emptyColumns());
        assertEquals(
                SurfaceSemanticOracle.normalize(result.blocks()),
                SurfaceSemanticOracle.normalize(List.of(result.blocks().getFirst()))
        );
    }

    @Test
    void legacyClassifierKeepsRepresentativeClassPrecedenceAndUnknown() {
        SurfaceClassifier classifier = new SurfaceClassifier();

        assertEquals(SurfaceClass.WATER, classifier.classify(
                new BlockInfo(1, "game:soil"),
                new BlockInfo(3, "game:water-still")));
        assertEquals(SurfaceClass.SNOW, classifier.classify(
                new BlockInfo(1, "game:snow-covered-soil"), BlockInfo.unknown(0)));
        assertEquals(SurfaceClass.ROCK, classifier.classify(
                new BlockInfo(1, "game:rock-sandstone"), BlockInfo.unknown(0)));
        assertEquals(SurfaceClass.UNKNOWN, classifier.classify(
                REGISTRY.get(99), BlockInfo.unknown(0)));
    }

    private MapChunk mapChunk(int height) {
        return new MapChunk(
                new MapChunkCoordinate(0, 0), heights(height), new int[0]);
    }

    private static int[] heights(int value) {
        int[] result = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(result, value);
        return result;
    }
}
