package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cartographer.geology.rock.RockCharacterizationFixtures.at;
import static cartographer.geology.rock.RockCharacterizationFixtures.chunk;
import static cartographer.geology.rock.RockCharacterizationFixtures.coverage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockCharacterizationTest {
    private static final RockCatalog CATALOG = RockCharacterizationFixtures.CATALOG;

    @Test
    void oracleFreezesUpperSemanticsAcrossVerticalGapsAndCoverageStates() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                at(1, 6, 1, 1)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                at(1, 8, 1, 2)
        );

        RockLegacyOracle.Snapshot complete = snapshot(
                List.of(lower, upper),
                coverage(
                        new ChunkCoordinate(0, 0, 0),
                        new ChunkCoordinate(0, 1, 0)
                ),
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        RockLegacyOracle.Cell observed = cellAt(complete, 1, 1);
        assertEquals(RockColumnState.OBSERVED, observed.state());
        assertEquals("game:rock-shale", observed.rockCode());
        assertEquals(40, observed.rockY());

        RockLegacyOracle.Snapshot lowerOnly = snapshot(
                List.of(lower),
                coverage(new ChunkCoordinate(0, 0, 0)),
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        assertEquals(
                RockColumnState.UNAVAILABLE,
                cellAt(lowerOnly, 1, 1).state()
        );

        RockLegacyOracle.Snapshot upperOnly = snapshot(
                List.of(upper),
                coverage(new ChunkCoordinate(0, 1, 0)),
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        assertEquals(RockColumnState.OBSERVED, cellAt(upperOnly, 1, 1).state());
        assertEquals(40, cellAt(upperOnly, 1, 1).rockY());

        RockLegacyOracle.Snapshot knownEmpty = snapshot(
                List.of(lower),
                coverage(
                        new ChunkCoordinate(0, 0, 0),
                        new ChunkCoordinate(0, 1, 0)
                ),
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        assertEquals(
                RockColumnState.OBSERVED,
                cellAt(knownEmpty, 1, 1).state()
        );

        RockLegacyOracle.Snapshot emptyComplete = snapshot(
                List.of(
                        chunk(new ChunkCoordinate(0, 0, 0)),
                        chunk(new ChunkCoordinate(0, 1, 0))
                ),
                coverage(
                        new ChunkCoordinate(0, 0, 0),
                        new ChunkCoordinate(0, 1, 0)
                ),
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        assertEquals(RockColumnState.NO_ROCK, cellAt(emptyComplete, 1, 1).state());
    }

    @Test
    void characterizesPartialRangesBoundariesAndMissingMiddleChunk() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                at(1, 4, 1, 1),
                at(1, 5, 1, 2)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                at(1, 1, 1, 2),
                at(1, 2, 1, 1)
        );

        RockMap partial = scan(
                List.of(lower, upper),
                coverage(new ChunkCoordinate(0, 0, 0), new ChunkCoordinate(0, 1, 0)),
                new WorldPosition(1, 0, 1),
                5,
                34
        );
        assertEquals(33, cellAt(RockLegacyOracle.snapshot(partial), 1, 1).rockY());

        RockMap lowerBoundInclusive = scan(
                List.of(lower),
                coverage(new ChunkCoordinate(0, 0, 0)),
                new WorldPosition(1, 0, 1),
                5,
                6
        );
        RockLegacyOracle.Cell lowerCell = cellAt(
                RockLegacyOracle.snapshot(lowerBoundInclusive), 1, 1
        );
        assertEquals(RockColumnState.OBSERVED, lowerCell.state());
        assertEquals(5, lowerCell.rockY());

        ParsedChunk secondUpper = chunk(new ChunkCoordinate(0, 2, 0));
        RockMap middleMissing = scan(
                List.of(lower, secondUpper),
                coverage(
                        new ChunkCoordinate(0, 0, 0),
                        new ChunkCoordinate(0, 2, 0)
                ),
                new WorldPosition(1, 0, 1),
                0,
                96
        );
        assertEquals(
                RockColumnState.UNAVAILABLE,
                cellAt(RockLegacyOracle.snapshot(middleMissing), 1, 1).state()
        );
    }

    @Test
    void legacyUpperResultIsIndependentOfUniqueChunkInputPermutation() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                at(1, 6, 1, 1)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                at(1, 8, 1, 2)
        );
        RockChunkCoverage complete = coverage(
                new ChunkCoordinate(0, 0, 0),
                new ChunkCoordinate(0, 1, 0)
        );

        RockLegacyOracle.Snapshot lowerFirst = snapshot(
                List.of(lower, upper),
                complete,
                new WorldPosition(1, 0, 1),
                0,
                64
        );
        RockLegacyOracle.Snapshot upperFirst = snapshot(
                List.of(upper, lower),
                complete,
                new WorldPosition(1, 0, 1),
                0,
                64
        );

        assertEquals(lowerFirst, upperFirst);
    }

    @Test
    void characterizesNegativeCoordinatesModdedRocksAndExcludedBlocks() {
        ParsedChunk negative = chunk(
                new ChunkCoordinate(-1, 0, -1),
                at(31, 1, 31, 3),
                at(30, 2, 31, 4)
        );
        RockMap map = scan(
                List.of(negative),
                coverage(new ChunkCoordinate(-1, 0, -1)),
                new WorldPosition(-0.2, 0, -0.2),
                0,
                32
        );
        RockLegacyOracle.Snapshot snapshot = RockLegacyOracle.snapshot(map);
        assertEquals("somemod:rock-gneiss", cellAt(snapshot, -1, -1).rockCode());
        assertEquals(RockColumnState.NO_ROCK, cellAt(snapshot, -2, -1).state());
    }

    @Test
    void legacyResultIsDeterministicRowMajorAndUsesExactCircle() {
        ParsedChunk empty = chunk(new ChunkCoordinate(0, 0, 0));
        RockLegacyOracle.Snapshot first = RockLegacyOracle.snapshot(
                scan(
                        List.of(empty),
                        coverage(new ChunkCoordinate(0, 0, 0)),
                        new WorldPosition(0, 0, 0),
                        0,
                        32,
                        2
                )
        );
        RockLegacyOracle.Snapshot second = RockLegacyOracle.snapshot(
                scan(
                        List.of(empty),
                        coverage(new ChunkCoordinate(0, 0, 0)),
                        new WorldPosition(0, 0, 0),
                        0,
                        32,
                        2
                )
        );

        assertEquals(first, second);
        assertEquals(13, first.cells().size());
        assertEquals(
                List.of(
                        "0,-2",
                        "-1,-1", "0,-1", "1,-1",
                        "-2,0", "-1,0", "0,0", "1,0", "2,0",
                        "-1,1", "0,1", "1,1",
                        "0,2"
                ),
                first.cells().stream()
                        .map(cell -> cell.worldX() + "," + cell.worldZ())
                        .toList()
        );
        for (int index = 1; index < first.cells().size(); index++) {
            RockLegacyOracle.Cell previous = first.cells().get(index - 1);
            RockLegacyOracle.Cell current = first.cells().get(index);
            assertTrue(
                    previous.worldZ() < current.worldZ()
                            || previous.worldZ() == current.worldZ()
                            && previous.worldX() <= current.worldX()
            );
        }
        assertTrue(first.cells().stream().noneMatch(cell ->
                Math.abs(cell.worldX()) == 2 && Math.abs(cell.worldZ()) == 1
        ));
    }

    private RockLegacyOracle.Snapshot snapshot(
            List<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            WorldPosition center,
            int minY,
            int maxY
    ) {
        return RockLegacyOracle.snapshot(scan(chunks, coverage, center, minY, maxY));
    }

    private RockMap scan(
            List<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            WorldPosition center,
            int minY,
            int maxY
    ) {
        return scan(chunks, coverage, center, minY, maxY, 1);
    }

    private RockMap scan(
            List<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            WorldPosition center,
            int minY,
            int maxY,
            int radius
    ) {
        return new RockColumnScanner().scan(
                chunks,
                CATALOG,
                center,
                radius,
                minY,
                maxY,
                coverage
        );
    }

    private RockLegacyOracle.Cell cellAt(
            RockLegacyOracle.Snapshot snapshot,
            int x,
            int z
    ) {
        return snapshot.cells().stream()
                .filter(cell -> cell.worldX() == x && cell.worldZ() == z)
                .findFirst()
                .orElseThrow();
    }
}
