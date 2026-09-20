package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RockMapCompactTest {
    private static final RockIdentity GRANITE = new RockIdentity(10, "game:rock-granite", "game", "granite");
    private static final RockIdentity GRANITE_ALIAS = new RockIdentity(11, "game:rock-granite", "game", "granite");
    private static final RockIdentity SHALE = new RockIdentity(20, "game:rock-shale", "game", "shale");

    @Test
    void storesAggregatesAndMaterializesCompatibilitySamplesOnDemand() {
        RockCatalog catalog = RockCatalog.from(Map.of(
                GRANITE.blockId(),
                new BlockInfo(GRANITE.blockId(), GRANITE.code())
        ));
        RockMapAssembler assembler = new RockMapAssembler(
                new WorldPosition(0, 0, 0),
                1,
                0,
                8,
                RockMapMode.UPPER_ROCK,
                catalog
        );
        assembler.accept(RockColumnSample.observed(0, 0, GRANITE, 7));
        assembler.accept(RockColumnSample.noRock(0, -1));
        assembler.accept(RockColumnSample.unavailable(-1, 0));
        RockMap map = assembler.finish();
        assertEquals(1, map.observedCount());
        assertEquals(1, map.noRockCount());
        assertEquals(1, map.unavailableCount());
        assertEquals(RockColumnState.OBSERVED, map.sampleAt(0, 0).orElseThrow().state());
        assertEquals(7, map.sampleAt(0, 0).orElseThrow().rockY().orElseThrow());
        assertEquals(
                RockColumnState.NO_ROCK,
                map.sampleAt(0, -1).orElseThrow().state()
        );
        assertEquals(
                RockColumnState.UNAVAILABLE,
                map.sampleAt(-1, 0).orElseThrow().state()
        );
    }

    @Test
    void mapsEqualSemanticIdentitiesToOneAscendingBlockIdOrdinal() {
        Map<Integer, BlockInfo> registry = new LinkedHashMap<>();
        registry.put(20, new BlockInfo(20, SHALE.code()));
        registry.put(11, new BlockInfo(11, GRANITE_ALIAS.code()));
        registry.put(10, new BlockInfo(10, GRANITE.code()));
        RockCatalog catalog = RockCatalog.from(registry);
        RockMapAssembler assembler = new RockMapAssembler(
                new WorldPosition(0, 0, 0),
                1,
                0,
                8,
                RockMapMode.UPPER_ROCK,
                catalog
        );
        assembler.accept(
                RockColumnSample.observed(0, 0, GRANITE_ALIAS, 3)
        );
        assembler.accept(
                RockColumnSample.observed(0, -1, SHALE, 4)
        );
        RockMap map = assembler.finish();
        assertEquals(List.of("game:rock-granite", "game:rock-shale"),
                map.ordinalTable().stream().map(RockIdentity::code).toList());
        assertEquals(GRANITE.blockId(), map.ordinalTable().get(0).blockId());
        assertEquals(map.sampleAt(0, 0).orElseThrow().rock().orElseThrow().code(), GRANITE.code());
        assertEquals(List.of(0L, 1L, 1L), java.util.Arrays.stream(map.countsByOrdinal()).boxed().toList());
    }

    @Test
    void ownershipTransfersWithoutASecondPackedArray() {
        RockCatalog catalog = RockCatalog.from(Map.of(10, new BlockInfo(10, GRANITE.code())));
        RockMapBuilder builder = new RockMapBuilder(new WorldPosition(0, 0, 0), 1, 0, 8,
                RockMapMode.UPPER_ROCK, catalog);
        int storage = builder.packedStorageIdentityForTest();
        RockMap map = builder.finish();
        assertEquals(storage, map.packedStorageIdentityForTest());
        assertThrows(IllegalStateException.class, builder::finish);
    }
}
