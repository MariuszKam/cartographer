package cartographer.resource;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedSurfaceResourceCatalogTest {
    private final SurfaceObjectCandidateCatalogBuilder candidateBuilder =
            new SurfaceObjectCandidateCatalogBuilder();
    private final ObservedSurfaceResourceCatalogBuilder observedBuilder =
            new ObservedSurfaceResourceCatalogBuilder();

    @Test
    void includesOnlyObservedCandidatesAndGroupsAllFamilies() {
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(Map.of(
                1, block(1, "game:looseores-nativecopper-granite-free"),
                10, block(10, "game:loosestones-obsidian-free"),
                11, block(11, "game:looseflints-obsidian-free"),
                12, block(12, "game:looseboulders-obsidian-snow")
        ));

        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                List.of(
                        surface(12, 3, 20, 4),
                        surface(10, 1, 10, 2),
                        surface(11, 2, 10, 1)
                )
        );

        assertEquals(List.of("game:obsidian"),
                observed.observedQualifiedResourceKeys());
        assertEquals(3, observed.resources().getFirst().observedCount());
        assertEquals(List.of(11, 10, 12), observed.observations().stream()
                .map(SurfaceObjectObservation::blockId)
                .toList());
    }

    @Test
    void keepsNamespacesSeparateAndOrdersResourcesCanonically() {
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(
                new HashMap<>(Map.of(
                        2, block(2, "somemod:loosestones-something-free"),
                        1, block(1, "game:loosestones-something-free"),
                        3, block(3, "game:loosestones-obsidian-free")
                ))
        );

        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                List.of(
                        surface(2, 1, 1, 1),
                        surface(3, 1, 2, 1),
                        surface(1, 1, 3, 1)
                )
        );

        assertEquals(List.of("game:obsidian", "game:something", "somemod:something"),
                observed.observedQualifiedResourceKeys());
        assertEquals("game:something", observed.resources().get(1)
                .candidate().qualifiedResourceKey());
        assertEquals("somemod:something", observed.resources().get(2)
                .candidate().qualifiedResourceKey());
    }

    @Test
    void unknownBlockIdsAndEmptyInputProduceNoObservedResources() {
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(Map.of(
                1, block(1, "game:loosestones-obsidian-free")
        ));

        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                List.of(surface(99, 0, 0, 0))
        );
        ObservedSurfaceResourceCatalog empty = observedBuilder.build(
                candidates,
                List.of()
        );

        assertEquals(List.of(), observed.resources());
        assertEquals(List.of(), empty.observations());
        assertEquals(List.of(), empty.observedQualifiedResourceKeys());
    }

    @Test
    void observationsAndResourcesAreImmutable() {
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(Map.of(
                1, block(1, "game:loosestones-obsidian-free")
        ));
        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                List.of(surface(1, 1, 1, 1))
        );

        assertThrows(UnsupportedOperationException.class,
                () -> observed.resources().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> observed.observations().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> observed.resources().getFirst().observations().clear());
    }

    private BlockInfo block(int id, String code) {
        return new BlockInfo(id, code);
    }

    private SurfaceBlock surface(int blockId, int x, int y, int z) {
        return new SurfaceBlock(x, y, z, block(blockId, switch (blockId) {
            case 1 -> "game:loosestones-something-free";
            case 2 -> "somemod:loosestones-something-free";
            case 3 -> "game:loosestones-obsidian-free";
            case 10 -> "game:loosestones-obsidian-free";
            case 11 -> "game:looseflints-obsidian-free";
            case 12 -> "game:looseboulders-obsidian-snow";
            default -> "game:unknown";
        }));
    }
}
