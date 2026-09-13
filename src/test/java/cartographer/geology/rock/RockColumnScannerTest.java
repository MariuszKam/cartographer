package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockColumnScannerTest {

    private static final RockCatalog CATALOG = RockCatalog.from(Map.of(
            1, new BlockInfo(1, "game:rock-granite"),
            2, new BlockInfo(2, "game:rock-shale"),
            3, new BlockInfo(3, "somemod:rock-gneiss"),
            4, new BlockInfo(4, "game:ore-cassiterite-granite"),
            5, new BlockInfo(5, "game:soil-medium"),
            6, new BlockInfo(6, "game:water-still")
    ));

    @Test
    void findsUppermostNaturalRockAndWorldY() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 1, 1, 1),
                entry(1, 10, 1, 2),
                entry(1, 20, 1, 5)
        );

        RockColumnSample sample = scan(List.of(chunk), 1, 0, 32)
                .columns().stream()
                .filter(column -> column.worldX() == 1 && column.worldZ() == 1)
                .findFirst()
                .orElseThrow();

        assertEquals(RockColumnState.OBSERVED, sample.state());
        assertEquals("shale", sample.rock().orElseThrow().rockName());
        assertEquals(10, sample.rockY().orElseThrow());
    }

    @Test
    void supportsModdedRocksAndRejectsOreBlocks() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(2, 2, 2, 3),
                entry(3, 2, 2, 4)
        );
        RockMap map = scan(List.of(chunk), 2, 2, 32);

        RockColumnSample modded = sampleAt(map, 2, 2);
        RockColumnSample ore = sampleAt(map, 3, 2);
        assertEquals("gneiss", modded.rock().orElseThrow().rockName());
        assertEquals(RockColumnState.NO_ROCK, ore.state());
    }

    @Test
    void searchesAcrossVerticalChunksFromHighestDownward() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 1, 1, 1)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                entry(1, 1, 1, 5),
                entry(1, 8, 1, 2)
        );

        RockColumnSample sample = sampleAt(
                scan(List.of(lower, upper), 1, 0, 64),
                1,
                1
        );

        assertEquals("shale", sample.rock().orElseThrow().rockName());
        assertEquals(40, sample.rockY().orElseThrow());
    }

    @Test
    void rockOnlyInLowerChunkIsObservedWhenCoverageIsComplete() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(4, 1, 1, 1)
        );
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                entry(1, 1, 1, 5)
        );

        RockColumnSample sample = sampleAt(
                scan(List.of(lower, upper), 4, 0, 64),
                4,
                1
        );

        assertEquals(RockColumnState.OBSERVED, sample.state());
        assertEquals(1, sample.rockY().orElseThrow());
    }

    @Test
    void handlesNegativeAndNonZeroChunkCoordinates() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(-2, 0, -1),
                entry(31, 1, 31, 3)
        );

        RockColumnSample sample = sampleAt(
                scan(List.of(chunk), -33, -1, 32),
                -33,
                -1
        );

        assertEquals(RockColumnState.OBSERVED, sample.state());
        assertEquals("gneiss", sample.rock().orElseThrow().rockName());
    }

    @Test
    void floorsPositiveFractionalCenterToBlockCoordinate() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(10, 1, 10, 1)
        );

        RockColumnSample sample = sampleAt(
                scan(
                        List.of(chunk),
                        new WorldPosition(10.8, 0, 10.8),
                        1,
                        32
                ),
                10,
                10
        );

        assertEquals(RockColumnState.OBSERVED, sample.state());
    }

    @Test
    void floorsNegativeFractionalCenterToBlockCoordinate() {
        ParsedChunk chunk = chunk(
                new ChunkCoordinate(-1, 0, -1),
                entry(31, 1, 31, 3)
        );

        RockColumnSample sample = sampleAt(
                scan(
                        List.of(chunk),
                        new WorldPosition(-0.2, 0, -0.2),
                        1,
                        32
                ),
                -1,
                -1
        );

        assertEquals(RockColumnState.OBSERVED, sample.state());
    }

    @Test
    void rockAboveMissingLowerChunkRemainsObserved() {
        ParsedChunk upper = chunk(
                new ChunkCoordinate(0, 1, 0),
                entry(1, 8, 1, 2)
        );

        RockColumnSample sample = sampleAt(
                scan(
                        List.of(upper),
                        RockChunkCoverage.fromChunkCoordinates(
                                List.of(new ChunkCoordinate(0, 1, 0))
                        ),
                        1,
                        0,
                        64
                ),
                1,
                1
        );

        assertEquals(RockColumnState.OBSERVED, sample.state());
        assertEquals(40, sample.rockY().orElseThrow());
    }

    @Test
    void knownExistingUndecodedChunkCanProveNoRock() {
        ParsedChunk lower = chunk(new ChunkCoordinate(0, 0, 0));

        RockColumnSample sample = sampleAt(
                scan(
                        List.of(lower),
                        RockChunkCoverage.fromChunkCoordinates(
                                List.of(
                                        new ChunkCoordinate(0, 0, 0),
                                        new ChunkCoordinate(0, 1, 0)
                                )
                        ),
                        1,
                        0,
                        64
                ),
                1,
                1
        );

        assertEquals(RockColumnState.NO_ROCK, sample.state());
    }

    @Test
    void missingChunkRemainsUnavailable() {
        RockColumnSample sample = sampleAt(
                scan(
                        List.of(),
                        RockChunkCoverage.fromChunkCoordinates(
                                List.of(new ChunkCoordinate(0, 0, 0))
                        ),
                        1,
                        0,
                        64
                ),
                1,
                1
        );

        assertEquals(RockColumnState.UNAVAILABLE, sample.state());
    }

    @Test
    void ignoresColumnsOutsideRadiusAndOrdersResultsDeterministically() {
        RockMap map = scan(
                List.of(chunk(new ChunkCoordinate(0, 0, 0))),
                1,
                0,
                32
        );

        assertTrue(map.columns().stream().allMatch(column ->
                Math.hypot(column.worldX() - 1, column.worldZ()) <= 1
        ));
        assertEquals(
                map.columns().stream()
                        .sorted(java.util.Comparator.comparingInt(RockColumnSample::worldZ)
                                .thenComparingInt(RockColumnSample::worldX))
                        .toList(),
                map.columns()
        );
    }

    @Test
    void distinguishesUnavailableFromNoRock() {
        ParsedChunk noRock = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 1, 1, 5)
        );
        RockMap noRockMap = scan(List.of(noRock), 1, 0, 32);
        assertEquals(RockColumnState.NO_ROCK, sampleAt(noRockMap, 1, 1).state());

        RockMap unavailable = scan(List.of(), 1, 0, 32);
        assertEquals(
                RockColumnState.UNAVAILABLE,
                sampleAt(unavailable, 1, 1).state()
        );
    }

    @Test
    void missingUpperVerticalChunkRemainsUnavailableEvenWithLowerRock() {
        ParsedChunk lower = chunk(
                new ChunkCoordinate(0, 0, 0),
                entry(1, 1, 1, 1)
        );

        assertEquals(
                RockColumnState.UNAVAILABLE,
                sampleAt(scan(List.of(lower), 1, 0, 64), 1, 1).state()
        );
    }

    private RockMap scan(
            List<ParsedChunk> chunks,
            int centerX,
            int centerZ,
            int maxY
    ) {
        return new RockColumnScanner().scan(
                chunks,
                CATALOG,
                new WorldPosition(centerX, 0, centerZ),
                1,
                0,
                maxY
        );
    }

    private RockMap scan(
            Collection<ParsedChunk> chunks,
            RockChunkCoverage coverage,
            int centerX,
            int minY,
            int maxY
    ) {
        return new RockColumnScanner().scan(
                chunks,
                CATALOG,
                new WorldPosition(centerX, 0, 1),
                1,
                minY,
                maxY,
                coverage
        );
    }

    private RockMap scan(
            Collection<ParsedChunk> chunks,
            WorldPosition center,
            int radius,
            int maxY
    ) {
        return new RockColumnScanner().scan(
                chunks,
                CATALOG,
                center,
                radius,
                0,
                maxY
        );
    }

    private RockColumnSample sampleAt(RockMap map, int x, int z) {
        return map.columns().stream()
                .filter(column -> column.worldX() == x && column.worldZ() == z)
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
