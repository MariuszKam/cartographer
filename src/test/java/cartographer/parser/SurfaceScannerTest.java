package cartographer.parser;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceClassifier;
import cartographer.scanner.SurfaceScanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceScannerTest {
    @Test
    void findsTopMostNonAirAndCanIgnoreFoliage() {
        ParsedChunk chunk = new ParsedChunk(
                new ChunkCoordinate(0, 0),
                10,
                1,
                3,
                1,
                new int[]{1, 2, 3});
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:soil-medium"),
                3, new BlockInfo(3, "game:leaves-oak"));

        SurfaceScanResult result = new SurfaceScanner().scan(List.of(chunk), registry, true);

        assertEquals(1, result.blocks().size());
        assertEquals(11, result.blocks().getFirst().y());
        assertEquals("game:soil-medium", result.blocks().getFirst().blockInfo().code());
        assertEquals(SurfaceClass.SOIL, result.blocks().getFirst().surfaceClass());
    }

    @Test
    void usesHighestVerticalChunkSectionForSurfaceColumn() {
        ParsedChunk lower =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                0,
                                0
                        ),
                        0,
                        1,
                        2,
                        1,
                        new int[]{1, 1}
                );

        ParsedChunk upper =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                1,
                                0
                        ),
                        32,
                        1,
                        2,
                        1,
                        new int[]{2, 2}
                );

        Map<Integer, BlockInfo> registry =
                Map.of(
                        1,
                        new BlockInfo(
                                1,
                                "rock-granite"
                        ),
                        2,
                        new BlockInfo(
                                2,
                                "soil-medium"
                        )
                );

        SurfaceScanResult result =
                new SurfaceScanner()
                        .scan(
                                List.of(
                                        lower,
                                        upper
                                ),
                                registry,
                                true
                        );

        assertEquals(
                1,
                result.blocks()
                        .size()
        );

        assertEquals(
                33,
                result.blocks()
                        .getFirst()
                        .y()
        );

        assertEquals(
                SurfaceClass.SOIL,
                result.blocks()
                        .getFirst()
                        .surfaceClass()
        );

        assertEquals(
                1,
                result.columnsScanned()
        );

        assertEquals(
                0,
                result.emptyColumns()
        );
    }

    @Test
    void detectsWaterFromLiquidLayer() {
        ParsedChunk chunk =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                0,
                                0
                        ),
                        0,
                        1,
                        2,
                        1,
                        new int[]{0, 0},
                        new int[]{0, 7},
                        2
                );

        Map<Integer, BlockInfo> registry =
                Map.of(
                        0,
                        new BlockInfo(
                                0,
                                "air"
                        ),
                        7,
                        new BlockInfo(
                                7,
                                "water-still-7"
                        )
                );

        SurfaceScanResult result =
                new SurfaceScanner()
                        .scan(
                                List.of(chunk),
                                registry,
                                true
                        );

        assertEquals(
                1,
                result.waterColumns()
        );

        assertEquals(
                SurfaceClass.WATER,
                result.blocks()
                        .getFirst()
                        .surfaceClass()
        );
    }

    @Test
    void reportsUnavailableLiquidLayerWithoutClaimingWater() {
        ParsedChunk chunk =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                0,
                                0
                        ),
                        0,
                        1,
                        1,
                        1,
                        new int[]{1},
                        new int[]{0},
                        2,
                        false,
                        "liquidsCompressed: corrupt"
                );

        Map<Integer, BlockInfo> registry =
                Map.of(
                        1,
                        new BlockInfo(
                                1,
                                "game:soil-medium"
                        )
                );

        SurfaceScanResult result =
                new SurfaceScanner()
                        .scan(
                                List.of(chunk),
                                registry,
                                true
                        );

        assertEquals(
                1,
                result.liquidUnavailableColumns()
        );

        assertEquals(
                0,
                result.waterColumns()
        );
    }

    @Test
    void countsEmptyColumnsOnceAcrossVerticalChunkSections() {
        ParsedChunk lower =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                0,
                                0
                        ),
                        0,
                        1,
                        1,
                        1,
                        new int[]{0}
                );

        ParsedChunk upper =
                new ParsedChunk(
                        new ChunkCoordinate(
                                0,
                                1,
                                0
                        ),
                        32,
                        1,
                        1,
                        1,
                        new int[]{0}
                );

        SurfaceScanResult result =
                new SurfaceScanner()
                        .scan(
                                List.of(
                                        lower,
                                        upper
                                ),
                                Map.of(
                                        0,
                                        new BlockInfo(
                                                0,
                                                "air"
                                        )
                                ),
                                true
                        );

        assertEquals(
                1,
                result.columnsScanned()
        );

        assertEquals(
                1,
                result.emptyColumns()
        );
    }

    @Test
    void preservesUnknownForUnclassifiedModdedBlocks() {
        SurfaceClass surfaceClass =
                new SurfaceClassifier()
                        .classify(
                                new BlockInfo(
                                        99,
                                        "othermod:mystery-surface"
                                ),
                                BlockInfo.unknown(0)
                        );

        assertEquals(
                SurfaceClass.UNKNOWN,
                surfaceClass
        );
    }
}
