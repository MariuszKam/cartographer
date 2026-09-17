package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;

import java.util.List;
import java.util.Map;

/** Small deterministic test fixtures; this class does not scan ROCK data. */
final class RockCharacterizationFixtures {
    static final RockCatalog CATALOG = RockCatalog.from(Map.of(
            1, new BlockInfo(1, "game:rock-granite"),
            2, new BlockInfo(2, "game:rock-shale"),
            3, new BlockInfo(3, "somemod:rock-gneiss"),
            4, new BlockInfo(4, "game:ore-cassiterite-granite"),
            5, new BlockInfo(5, "game:soil-medium"),
            6, new BlockInfo(6, "game:water-still")
    ));

    private RockCharacterizationFixtures() {
    }

    static ParsedChunk chunk(ChunkCoordinate coordinate, BlockAt... entries) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        for (BlockAt entry : entries) {
            blocks[(entry.y() * size + entry.z()) * size + entry.x()] = entry.id();
        }
        return new ParsedChunk(
                coordinate,
                coordinate.y() * size,
                size,
                size,
                size,
                blocks
        );
    }

    static BlockAt at(int x, int y, int z, int id) {
        return new BlockAt(x, y, z, id);
    }

    static RockChunkCoverage coverage(ChunkCoordinate... coordinates) {
        return RockChunkCoverage.fromChunkCoordinates(List.of(coordinates));
    }

    record BlockAt(int x, int y, int z, int id) {
    }
}
