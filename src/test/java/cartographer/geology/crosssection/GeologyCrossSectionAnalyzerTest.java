package cartographer.geology.crosssection;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeologyCrossSectionAnalyzerTest {

    private static final int CHUNK_SIZE =
            ChunkCoordinate.SIZE_BLOCKS;

    private static final Map<Integer, BlockInfo> REGISTRY =
            Map.of(
                    0,
                    new BlockInfo(
                            0,
                            "air"
                    ),
                    1,
                    new BlockInfo(
                            1,
                            "rock-granite"
                    ),
                    2,
                    new BlockInfo(
                            2,
                            "ore-poor-nativecopper-granite"
                    ),
                    3,
                    new BlockInfo(
                            3,
                            "rock-limestone"
                    )
            );

    private final GeologyCrossSectionAnalyzer analyzer =
            new GeologyCrossSectionAnalyzer();

    @Test
    void crossesPositiveChunkBoundary() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        0,
                                        0,
                                        0,
                                        1
                                ),
                                chunk(
                                        1,
                                        0,
                                        0,
                                        3
                                )
                        ),
                        REGISTRY,
                        31,
                        0,
                        32,
                        0
                );

        assertEquals(
                2,
                section.columns()
                        .size()
        );

        GeologySectionColumn first =
                section.columns()
                        .get(0);

        GeologySectionColumn second =
                section.columns()
                        .get(1);

        assertEquals(
                31,
                first.worldX()
        );

        assertEquals(
                32,
                second.worldX()
        );

        assertEquals(
                List.of(
                        new GeologySectionRun(
                                0,
                                32,
                                true,
                                1,
                                "rock-granite"
                        )
                ),
                first.runs()
        );

        assertEquals(
                List.of(
                        new GeologySectionRun(
                                0,
                                32,
                                true,
                                3,
                                "rock-limestone"
                        )
                ),
                second.runs()
        );
    }

    @Test
    void crossesNegativeCoordinateBoundary() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        -1,
                                        0,
                                        0,
                                        1
                                ),
                                chunk(
                                        0,
                                        0,
                                        0,
                                        3
                                )
                        ),
                        REGISTRY,
                        -1,
                        0,
                        0,
                        0
                );

        assertEquals(
                -1,
                section.columns()
                        .get(0)
                        .worldX()
        );

        assertEquals(
                0,
                section.columns()
                        .get(1)
                        .worldX()
        );

        assertEquals(
                "rock-granite",
                section.columns()
                        .get(0)
                        .runs()
                        .getFirst()
                        .blockCode()
        );

        assertEquals(
                "rock-limestone",
                section.columns()
                        .get(1)
                        .runs()
                        .getFirst()
                        .blockCode()
        );
    }

    @Test
    void combinesMultipleVerticalChunkLevels() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        0,
                                        0,
                                        0,
                                        1
                                ),
                                chunk(
                                        0,
                                        1,
                                        0,
                                        3
                                )
                        ),
                        REGISTRY,
                        0,
                        0,
                        0,
                        0
                );

        assertEquals(
                0,
                section.minYInclusive()
        );

        assertEquals(
                64,
                section.maxYExclusive()
        );

        assertEquals(
                List.of(
                        new GeologySectionRun(
                                0,
                                32,
                                true,
                                1,
                                "rock-granite"
                        ),
                        new GeologySectionRun(
                                32,
                                64,
                                true,
                                3,
                                "rock-limestone"
                        )
                ),
                section.columns()
                        .getFirst()
                        .runs()
        );
    }

    @Test
    void preservesCavesAndOreInsideHostRock() {
        ParsedChunk chunk =
                chunk(
                        0,
                        0,
                        0,
                        1,
                        new BlockOverride(
                                0,
                                10,
                                0,
                                0
                        ),
                        new BlockOverride(
                                0,
                                11,
                                0,
                                2
                        )
                );

        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk
                        ),
                        REGISTRY,
                        0,
                        0,
                        0,
                        0
                );

        assertEquals(
                List.of(
                        new GeologySectionRun(
                                0,
                                10,
                                true,
                                1,
                                "rock-granite"
                        ),
                        new GeologySectionRun(
                                10,
                                11,
                                true,
                                0,
                                "air"
                        ),
                        new GeologySectionRun(
                                11,
                                12,
                                true,
                                2,
                                "ore-poor-nativecopper-granite"
                        ),
                        new GeologySectionRun(
                                12,
                                32,
                                true,
                                1,
                                "rock-granite"
                        )
                ),
                section.columns()
                        .getFirst()
                        .runs()
        );
    }

    @Test
    void missingVerticalChunkIsUnavailableNotAir() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        0,
                                        0,
                                        0,
                                        1
                                ),
                                chunk(
                                        0,
                                        2,
                                        0,
                                        1
                                )
                        ),
                        REGISTRY,
                        0,
                        0,
                        0,
                        0
                );

        List<GeologySectionRun> runs =
                section.columns()
                        .getFirst()
                        .runs();

        assertEquals(
                3,
                runs.size()
        );

        assertTrue(
                runs.get(0)
                        .observed()
        );

        assertFalse(
                runs.get(1)
                        .observed()
        );

        assertEquals(
                32,
                runs.get(1)
                        .minYInclusive()
        );

        assertEquals(
                64,
                runs.get(1)
                        .maxYExclusive()
        );

        assertEquals(
                "unavailable",
                runs.get(1)
                        .blockCode()
        );

        assertTrue(
                runs.get(2)
                        .observed()
        );
    }

    @Test
    void rasterizesDiagonalLineBlockByBlock() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        0,
                                        0,
                                        0,
                                        1
                                )
                        ),
                        REGISTRY,
                        0,
                        0,
                        3,
                        3
                );

        assertEquals(
                4,
                section.columns()
                        .size()
        );

        assertCoordinates(
                section.columns()
                        .get(0),
                0,
                0
        );

        assertCoordinates(
                section.columns()
                        .get(1),
                1,
                1
        );

        assertCoordinates(
                section.columns()
                        .get(2),
                2,
                2
        );

        assertCoordinates(
                section.columns()
                        .get(3),
                3,
                3
        );
    }

    @Test
    void keepsObservedUnknownBlockIdDistinctFromUnavailableData() {
        GeologyCrossSection section =
                analyzer.analyze(
                        List.of(
                                chunk(
                                        0,
                                        0,
                                        0,
                                        99
                                )
                        ),
                        REGISTRY,
                        0,
                        0,
                        0,
                        0
                );

        GeologySectionRun run =
                section.columns()
                        .getFirst()
                        .runs()
                        .getFirst();

        assertTrue(
                run.observed()
        );

        assertEquals(
                99,
                run.blockId()
        );

        assertEquals(
                "unknown:99",
                run.blockCode()
        );
    }

    private void assertCoordinates(
            GeologySectionColumn column,
            int expectedX,
            int expectedZ
    ) {
        assertEquals(
                expectedX,
                column.worldX()
        );

        assertEquals(
                expectedZ,
                column.worldZ()
        );
    }

    private ParsedChunk chunk(
            int chunkX,
            int chunkY,
            int chunkZ,
            int defaultBlockId,
            BlockOverride... overrides
    ) {
        int[] blockIds =
                new int[
                        CHUNK_SIZE
                                * CHUNK_SIZE
                                * CHUNK_SIZE
                        ];

        Arrays.fill(
                blockIds,
                defaultBlockId
        );

        for (BlockOverride override : overrides) {
            blockIds[
                    index(
                            override.localX(),
                            override.localY(),
                            override.localZ()
                    )
                    ] =
                    override.blockId();
        }

        return cartographer.model.ParsedChunkFixtures.create(
                new ChunkCoordinate(
                        chunkX,
                        chunkY,
                        chunkZ
                ),
                chunkY
                        * CHUNK_SIZE,
                CHUNK_SIZE,
                CHUNK_SIZE,
                CHUNK_SIZE,
                blockIds
        );
    }

    private int index(
            int localX,
            int localY,
            int localZ
    ) {
        return (
                localY
                        * CHUNK_SIZE
                        + localZ
        )
                * CHUNK_SIZE
                + localX;
    }

    private record BlockOverride(
            int localX,
            int localY,
            int localZ,
            int blockId
    ) {
    }
}
