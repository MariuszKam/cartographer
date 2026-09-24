package cartographer.resource;

import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceObjectCompactFixtures;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cartographer.scanner.SurfaceObjectCompactFixtures.observation;
import static cartographer.scanner.SurfaceObjectCompactFixtures.scan;
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
                scan(
                        surface(12, 3, 20, 4),
                        surface(10, 1, 10, 2),
                        surface(11, 2, 10, 1)
                )
        );

        assertEquals(List.of("game:obsidian"),
                observed.resources().stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey())
                        .toList());
        assertEquals(3, observed.resources().getFirst().observedCount());
        assertEquals(List.of(11, 10, 12), observed.resources().stream()
                        .flatMap(resource -> resource.observations().stream())
                        .toList().stream()
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
                scan(
                        surface(2, 1, 1, 1),
                        surface(3, 1, 2, 1),
                        surface(1, 1, 3, 1)
                )
        );

        assertEquals(List.of("game:obsidian", "game:something", "somemod:something"),
                observed.resources().stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey())
                        .toList());
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
                scan(surface(99, 0, 0, 0))
        );
        ObservedSurfaceResourceCatalog empty = observedBuilder.build(
                candidates,
                scan()
        );

        assertEquals(List.of(), observed.resources());
        assertEquals(List.of(), empty.resources().stream()
                        .flatMap(resource -> resource.observations().stream())
                        .toList());
        assertEquals(List.of(), empty.resources().stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey())
                        .toList());
    }

    @Test
    void observationsAndResourcesAreImmutable() {
        SurfaceObjectCandidateCatalog candidates = candidateBuilder.build(Map.of(
                1, block(1, "game:loosestones-obsidian-free")
        ));
        ObservedSurfaceResourceCatalog observed = observedBuilder.build(
                candidates,
                scan(surface(1, 1, 1, 1))
        );

        assertThrows(UnsupportedOperationException.class,
                () -> observed.resources().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> observed.resources().getFirst().observations().clear());
    }

    private BlockInfo block(int id, String code) {
        return new BlockInfo(id, code);
    }

    private SurfaceObjectCompactFixtures.Observation surface(
            int blockId,
            int x,
            int y,
            int z
    ) {
        return observation(x, y, z, blockId);
    }
}
