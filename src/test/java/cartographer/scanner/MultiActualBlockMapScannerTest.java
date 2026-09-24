package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiActualBlockMapScannerTest {

    @Test
    void scansSeveralResourcesFromTheSameChunkPass() {
        ParsedChunk chunk = chunk(
                new BlockAt(0, 5, 0, 1),
                new BlockAt(1, 5, 0, 2)
        );

        List<ActualBlockMap> maps = scan(
                List.of(chunk),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(2, maps.size());
        assertEquals(1, maps.get(0).matchingBlocks());
        assertEquals(1, maps.get(1).matchingBlocks());
    }

    @Test
    void keepsResourcesInTheSameColumnIndependent() {
        ParsedChunk chunk = chunk(
                new BlockAt(0, 5, 0, 1),
                new BlockAt(0, 20, 0, 2)
        );

        List<ActualBlockMap> maps = scan(
                List.of(chunk),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(1, maps.get(0).hitColumns());
        assertEquals(1, maps.get(1).hitColumns());
        assertEquals(5, maps.get(0).minMatchedY());
        assertEquals(20, maps.get(1).minMatchedY());
    }

    @Test
    void appliesYFilterToEveryResource() {
        ParsedChunk chunk = chunk(
                new BlockAt(0, 5, 0, 1),
                new BlockAt(1, 20, 0, 1),
                new BlockAt(2, 5, 0, 2),
                new BlockAt(3, 20, 0, 2)
        );

        List<ActualBlockMap> maps = scan(
                List.of(chunk),
                new ActualBlockYFilter(16, null)
        );

        assertEquals(1, maps.get(0).matchingBlocks());
        assertEquals(1, maps.get(1).matchingBlocks());
        assertEquals(20, maps.get(0).minMatchedY());
        assertEquals(20, maps.get(1).minMatchedY());
    }

    @Test
    void includesZeroHitResource() {
        List<ActualBlockMap> maps = scan(
                List.of(chunk(new BlockAt(0, 5, 0, 1))),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(0, maps.get(1).matchingBlocks());
        assertEquals(0, maps.get(1).hitColumns());
        assertEquals(-1, maps.get(1).minMatchedY());
    }

    @Test
    void rejectsDuplicateMatches() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MultiActualBlockMapScanner().scan(
                        List.of(),
                        registry(),
                        0,
                        0,
                        16,
                        List.of("nativecopper", "NativeCopper"),
                        ActualBlockYFilter.unbounded()
                )
        );
    }

    @Test
    void ignoresNonOreRegistryBlocksInMultiMode() {
        List<ActualBlockMap> maps = new MultiActualBlockMapScanner().scan(
                List.of(chunk(new BlockAt(0, 5, 0, 3))),
                Map.of(
                        3, new BlockInfo(3, "forest-nativecopper")
                ),
                0,
                0,
                16,
                List.of("nativecopper"),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(0, maps.getFirst().matchingBlocks());
    }

    @Test
    void supportsNamespacedOreRegistryCodes() {
        List<ActualBlockMap> maps = new MultiActualBlockMapScanner().scan(
                List.of(chunk(
                        new BlockAt(0, 5, 0, 1),
                        new BlockAt(1, 5, 0, 2)
                )),
                Map.of(
                        1, new BlockInfo(1, "game:ore-nativecopper-granite"),
                        2, new BlockInfo(2, "mod:ore-cassiterite-granite")
                ),
                0,
                0,
                16,
                List.of("nativecopper", "cassiterite"),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(1, maps.get(0).matchingBlocks());
        assertEquals(1, maps.get(1).matchingBlocks());
    }

    @Test
    void ignoresOreSubstringOutsideOrePath() {
        List<ActualBlockMap> maps = new MultiActualBlockMapScanner().scan(
                List.of(chunk(new BlockAt(0, 5, 0, 3))),
                Map.of(
                        3, new BlockInfo(3, "game:decorative-ore-nativecopper")
                ),
                0,
                0,
                16,
                List.of("nativecopper"),
                ActualBlockYFilter.unbounded()
        );

        assertEquals(0, maps.getFirst().matchingBlocks());
    }

    @Test
    void streamingSessionMatchesListScan() {
        ParsedChunk chunk = chunk(
                new BlockAt(0, 5, 0, 1),
                new BlockAt(1, 5, 0, 2)
        );
        MultiActualBlockMapScanner scanner = new MultiActualBlockMapScanner();
        List<ActualBlockMap> listResult = scanner.scan(
                List.of(chunk),
                registry(),
                0,
                0,
                16,
                List.of("nativecopper", "cassiterite"),
                ActualBlockYFilter.unbounded()
        );
        MultiActualBlockMapScanner.StreamingSession session = scanner.begin(
                registry(),
                0,
                0,
                16,
                List.of(
                        new ActualBlockMatchSpec("nativecopper", ActualBlockMatchMode.ORE_CODE),
                        new ActualBlockMatchSpec("cassiterite", ActualBlockMatchMode.ORE_CODE)
                ),
                ActualBlockYFilter.unbounded()
        );
        session.accept(chunk);

        assertEquals(listResult, session.finish());
    }

    @Test
    void matchModesAreExplicit() {
        ParsedChunk chunk = chunk(
                new BlockAt(0, 5, 0, 1),
                new BlockAt(1, 5, 0, 2)
        );
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "decorative-ore-cassiterite")
        );
        MultiActualBlockMapScanner scanner = new MultiActualBlockMapScanner();

        MultiActualBlockMapScanner.StreamingSession ore = scanner.begin(
                registry,
                0,
                0,
                16,
                List.of(new ActualBlockMatchSpec("cassiterite", ActualBlockMatchMode.ORE_CODE)),
                ActualBlockYFilter.unbounded()
        );
        MultiActualBlockMapScanner.StreamingSession generic = scanner.begin(
                registry,
                0,
                0,
                16,
                List.of(new ActualBlockMatchSpec("cassiterite", ActualBlockMatchMode.GENERIC_SUBSTRING)),
                ActualBlockYFilter.unbounded()
        );
        ore.accept(chunk);
        generic.accept(chunk);

        assertEquals(1, ore.finish().getFirst().matchingBlocks());
        assertEquals(2, generic.finish().getFirst().matchingBlocks());
    }

    @Test
    void singleOreSpecMatchesSameResultAsWithAnotherOreSpec() {
        ParsedChunk chunk = chunk(new BlockAt(0, 5, 0, 1));
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "decorative-ore-cassiterite")
        );
        MultiActualBlockMapScanner scanner = new MultiActualBlockMapScanner();
        ActualBlockMatchSpec cassiterite =
                new ActualBlockMatchSpec("cassiterite", ActualBlockMatchMode.ORE_CODE);

        MultiActualBlockMapScanner.StreamingSession single = scanner.begin(
                registry, 0, 0, 16, List.of(cassiterite), ActualBlockYFilter.unbounded()
        );
        MultiActualBlockMapScanner.StreamingSession multiple = scanner.begin(
                registry,
                0,
                0,
                16,
                List.of(
                        cassiterite,
                        new ActualBlockMatchSpec("nativecopper", ActualBlockMatchMode.ORE_CODE)
                ),
                ActualBlockYFilter.unbounded()
        );
        single.accept(chunk);
        multiple.accept(chunk);

        assertEquals(single.finish().getFirst(), multiple.finish().getFirst());
    }

    private List<ActualBlockMap> scan(
            List<ParsedChunk> chunks,
            ActualBlockYFilter yFilter
    ) {
        return new MultiActualBlockMapScanner().scan(
                chunks,
                registry(),
                0,
                0,
                16,
                List.of("nativecopper", "cassiterite"),
                yFilter
        );
    }

    private Map<Integer, BlockInfo> registry() {
        return Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "ore-nativecopper-granite"),
                2, new BlockInfo(2, "ore-cassiterite-granite")
        );
    }

    private ParsedChunk chunk(BlockAt... blocksAt) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        Arrays.fill(blocks, 0);
        for (BlockAt block : blocksAt) {
            blocks[(block.y() * size + block.z()) * size + block.x()] = block.id();
        }
        return cartographer.model.ParsedChunkFixtures.create(
                new ChunkCoordinate(0, 0, 0),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private record BlockAt(int x, int y, int z, int id) {
    }
}
