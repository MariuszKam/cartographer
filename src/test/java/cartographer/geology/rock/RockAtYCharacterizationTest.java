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

class RockAtYCharacterizationTest {
    private static final RockCatalog CATALOG = RockCharacterizationFixtures.CATALOG;

    @Test
    void inspectsExactlyTheRequestedYAtBoth32BlockBoundaries() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                at(1, 31, 1, 1),
                at(1, 30, 1, 2)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                at(1, 0, 1, 3),
                at(1, 1, 1, 2)
        );

        assertEquals(
                "game:rock-granite",
                sample(List.of(lower), coverage(lower.coordinate()), 31).rockCode()
        );
        assertEquals(
                RockColumnState.NO_ROCK,
                sample(List.of(upper), coverage(upper.coordinate()), 32).state()
        );
        assertEquals(
                "game:rock-shale",
                sample(List.of(upper), coverage(upper.coordinate()), 33).rockCode()
        );
    }

    @Test
    void distinguishesObservedNoRockPaletteRejectedAndMissing() {
        ParsedChunk rock = chunk(
                new ChunkCoordinate(0, 0, 0),
                at(1, 4, 1, 1)
        );

        assertEquals(
                RockColumnState.OBSERVED,
                sample(List.of(rock), coverage(rock.coordinate()), 4).state()
        );
        assertEquals(
                RockColumnState.NO_ROCK,
                sample(
                        List.of(chunk(new ChunkCoordinate(0, 0, 0))),
                        coverage(new ChunkCoordinate(0, 0, 0)),
                        4
                ).state()
        );
        assertEquals(
                RockColumnState.NO_ROCK,
                sample(List.of(), coverage(new ChunkCoordinate(0, 0, 0)), 4).state()
        );
        assertEquals(
                RockColumnState.UNAVAILABLE,
                sample(List.of(), coverage(), 4).state()
        );
    }

    @Test
    void usesFloorSemanticsForNegativeFractionalHorizontalCoordinates() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(-1, 0, -1),
                at(31, 7, 31, 3)
        );

        RockColumnSample sample = new RockAtYScanner().scan(
                        List.of(chunk),
                        CATALOG,
                        coverage(chunk.coordinate()),
                        new WorldPosition(-0.2, 0, -0.2),
                        1,
                        7
                )
                .columns().stream()
                .filter(column -> column.worldX() == -1 && column.worldZ() == -1)
                .findFirst()
                .orElseThrow();

        assertEquals(RockColumnState.OBSERVED, sample.state());
        assertEquals("somemod:rock-gneiss", sample.rock().orElseThrow().code());
    }

    private RockLegacyOracle.Cell sample(
            List<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            int worldY
    ) {
        return RockLegacyOracle.snapshot(
                        new RockAtYScanner().scan(
                                chunks,
                                CATALOG,
                                coverage,
                                new WorldPosition(1, 0, 1),
                                1,
                                worldY
                        )
                )
                .cells().stream()
                .filter(cell -> cell.worldX() == 1 && cell.worldZ() == 1)
                .findFirst()
                .orElseThrow();
    }
}
