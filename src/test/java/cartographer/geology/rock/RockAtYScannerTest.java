package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RockAtYScannerTest {
    private static final RockCatalog CATALOG = RockCatalog.from(Map.of(
            1, new BlockInfo(1, "game:rock-granite"),
            2, new BlockInfo(2, "somemod:rock-gneiss"),
            3, new BlockInfo(3, "game:soil-medium")
    ));

    @Test
    void inspectsOnlyTheRequestedY() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 0, 1, 1),
                entry(1, 1, 1, 3),
                entry(1, 2, 1, 2)
        );

        RockColumnSample sample = sample(
                List.of(chunk),
                RockChunkCoverage.fromParsedChunks(List.of(chunk)),
                1
        );

        assertEquals(RockColumnState.NO_ROCK, sample.state());
    }

    @Test
    void convertsChunkBoundariesWithFloorDivision() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 31, 1, 1)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                entry(1, 0, 1, 2)
        );

        assertEquals(
                RockColumnState.OBSERVED,
                sample(
                        List.of(lower, upper),
                        RockChunkCoverage.fromParsedChunks(List.of(lower, upper)),
                        31
                ).state()
        );
        assertEquals(
                "gneiss",
                sample(
                        List.of(lower, upper),
                        RockChunkCoverage.fromParsedChunks(List.of(lower, upper)),
                        32
                ).rock().orElseThrow().rockName()
        );
    }

    @Test
    void distinguishesPaletteRejectedFromMissingChunk() {
        RockChunkCoverage available = RockChunkCoverage.fromChunkCoordinates(
                List.of(new ChunkCoordinate(0, 0, 0))
        );

        assertEquals(
                RockColumnState.NO_ROCK,
                sample(List.of(), available, 1).state()
        );
        assertEquals(
                RockColumnState.UNAVAILABLE,
                sample(List.of(), RockChunkCoverage.fromChunkCoordinates(List.of()), 1).state()
        );
    }

    private RockColumnSample sample(
            List<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            int y
    ) {
        return new RockAtYScanner().scan(
                        chunks,
                        CATALOG,
                        coverage,
                        new WorldPosition(1, 0, 1),
                        1,
                        y
                )
                .columns().stream()
                .filter(column -> column.worldX() == 1 && column.worldZ() == 1)
                .findFirst()
                .orElseThrow();
    }

    private ParsedChunk chunk(
            ChunkCoordinate coordinate,
            BlockAt... entries
    ) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        for (BlockAt entry : entries) {
            blocks[(entry.y * size + entry.z) * size + entry.x] = entry.id;
        }
        return new ParsedChunk(coordinate, coordinate.y() * size, size, size, size, blocks);
    }

    private BlockAt entry(int x, int y, int z, int id) {
        return new BlockAt(x, y, z, id);
    }

    private record BlockAt(int x, int y, int z, int id) {
    }
}
