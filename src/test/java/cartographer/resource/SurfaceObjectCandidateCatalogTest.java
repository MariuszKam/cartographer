package cartographer.resource;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectCandidateCatalogTest {
    private final SurfaceObjectCandidateCatalogBuilder builder =
            new SurfaceObjectCandidateCatalogBuilder();

    @Test
    void groupsOreVariantsAndHostRocksByOneLogicalResource() {
        SurfaceObjectCandidateCatalog catalog = builder.build(Map.of(
                2, block("game:looseores-nativecopper-basalt-free"),
                1, block("game:looseores-nativecopper-granite-snow"),
                3, block("game:looseores-nativecopper-granite-water")
        ));

        SurfaceObjectCandidate candidate = onlyCandidate(catalog);
        assertEquals("game:nativecopper", candidate.qualifiedResourceKey());
        assertEquals(Set.of(1, 2, 3), candidate.blockIds());
        assertEquals(Set.of(SurfaceObjectFamily.ORE_BITS), candidate.families());
    }

    @Test
    void groupsOneResourceAcrossSurfaceObjectFamilies() {
        SurfaceObjectCandidateCatalog catalog = builder.build(Map.of(
                12, block("game:looseboulders-obsidian-snow"),
                10, block("game:loosestones-obsidian-free"),
                11, block("game:looseflints-obsidian-free")
        ));

        SurfaceObjectCandidate candidate = onlyCandidate(catalog);
        assertEquals("game:obsidian", candidate.qualifiedResourceKey());
        assertEquals(Set.of(10, 11, 12), candidate.blockIds());
        assertEquals(
                Set.of(
                        SurfaceObjectFamily.FLINT,
                        SurfaceObjectFamily.LOOSE_BOULDER,
                        SurfaceObjectFamily.LOOSE_STONE
                ),
                candidate.families()
        );
    }

    @Test
    void keepsNamespacesAsDistinctCandidates() {
        SurfaceObjectCandidateCatalog catalog = builder.build(Map.of(
                21, block("somemod:loosestones-something-free"),
                20, block("game:loosestones-something-free")
        ));

        assertEquals(
                List.of("game:something", "somemod:something"),
                catalog.candidates().stream()
                        .map(SurfaceObjectCandidate::qualifiedResourceKey)
                        .toList()
        );
        assertEquals("game:something", catalog.findByBlockId(20).orElseThrow().qualifiedResourceKey());
        assertEquals("somemod:something", catalog.findByBlockId(21).orElseThrow().qualifiedResourceKey());
    }

    @Test
    void excludesUnsupportedBlocksAndExposesAllSupportedIds() {
        SurfaceObjectCandidateCatalog catalog = builder.build(Map.of(
                1, block("game:rock-granite"),
                2, block("game:ore-cassiterite-granite"),
                3, block("game:soil-medium-normal"),
                4, block("game:mushroom-something"),
                5, block("game:fireclay"),
                6, block("game:loosestones-obsidian-free")
        ));

        assertEquals(Set.of(6), catalog.candidateBlockIds());
        assertTrue(catalog.findByBlockId(1).isEmpty());
        assertTrue(catalog.findByQualifiedResourceKey("game:rock").isEmpty());
        assertTrue(catalog.findByQualifiedResourceKey(null).isEmpty());
    }

    @Test
    void isDeterministicForDifferentRegistryInsertionOrders() {
        Map<Integer, BlockInfo> first = new HashMap<>();
        first.put(30, block("game:looseflints-obsidian-free"));
        first.put(10, block("somemod:loosestones-something-free"));
        first.put(20, block("game:loosestones-obsidian-free"));

        Map<Integer, BlockInfo> second = new HashMap<>();
        second.put(20, block("game:loosestones-obsidian-free"));
        second.put(30, block("game:looseflints-obsidian-free"));
        second.put(10, block("somemod:loosestones-something-free"));

        SurfaceObjectCandidateCatalog firstCatalog = builder.build(first);
        SurfaceObjectCandidateCatalog secondCatalog = builder.build(second);

        assertEquals(firstCatalog.candidates(), secondCatalog.candidates());
        assertEquals(firstCatalog.candidateBlockIds(), secondCatalog.candidateBlockIds());
    }

    @Test
    void emptyRegistryProducesEmptyCatalog() {
        SurfaceObjectCandidateCatalog catalog = builder.build(Map.of());

        assertTrue(catalog.candidates().isEmpty());
        assertTrue(catalog.candidateBlockIds().isEmpty());
    }

    @Test
    void candidateCollectionsAreImmutable() {
        SurfaceObjectCandidate candidate = onlyCandidate(
                builder.build(Map.of(1, block("game:loosestones-obsidian-free")))
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> candidate.blockIds().add(2)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> candidate.families().clear()
        );
    }

    private SurfaceObjectCandidate onlyCandidate(SurfaceObjectCandidateCatalog catalog) {
        return catalog.candidates().getFirst();
    }

    private BlockInfo block(String code) {
        return new BlockInfo(0, code);
    }
}
