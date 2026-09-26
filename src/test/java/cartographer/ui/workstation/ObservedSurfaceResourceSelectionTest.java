package cartographer.ui.workstation;

import cartographer.model.BlockInfo;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.ObservedSurfaceResourceCatalogBuilder;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.scanner.SurfaceObjectCompactFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cartographer.scanner.SurfaceObjectCompactFixtures.observation;
import static cartographer.scanner.SurfaceObjectCompactFixtures.scan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedSurfaceResourceSelectionTest {
    private final SurfaceObjectCandidateCatalogBuilder candidates =
            new SurfaceObjectCandidateCatalogBuilder();
    private final ObservedSurfaceResourceCatalogBuilder observed =
            new ObservedSurfaceResourceCatalogBuilder();

    @Test
    void preservesCanonicalSelectionAndFallsBackToFirst() {
        ObservedSurfaceResourceCatalog catalog = catalog();
        List<ObservedSurfaceResource> resources = catalog.resources();

        assertEquals("game:obsidian", ObservedSurfaceResourceSelection
                .preserve("game:obsidian", resources).orElseThrow()
                .candidate().qualifiedResourceKey());
        assertEquals("game:nativecopper", ObservedSurfaceResourceSelection
                .preserve("game:missing", resources).orElseThrow()
                .candidate().qualifiedResourceKey());
        assertTrue(ObservedSurfaceResourceSelection
                .preserve("game:obsidian", List.of()).isEmpty());
    }

    @Test
    void keepsNamespaceVisibleInPresentation() {
        ObservedSurfaceResourceCatalog catalog = observed.build(
                candidates.build(Map.of(
                        1, new BlockInfo(1, "game:loosestones-something-free"),
                        2, new BlockInfo(2, "somemod:loosestones-something-free")
                )),
                scan(
                        surface(0, 1),
                        surface(1, 2)
                )
        );

        assertEquals("Something [somemod]", ObservedSurfaceResourceSelection
                .displayName(catalog.resources().get(1)));
    }

    @Test
    void presentsFamilyAndOccurrenceCountForDropdownAndStatus() {
        ObservedSurfaceResource resource = catalog().resources().getFirst();

        assertTrue(ObservedSurfaceResourceSelection.dropdownLabel(resource)
                .contains("Loose stone"));
        assertTrue(ObservedSurfaceResourceSelection.dropdownLabel(resource)
                .contains("1 found"));
        assertTrue(ObservedSurfaceResourceSelection.statusText(resource)
                .contains("Observed: 1 occurrences"));
        assertTrue(ObservedSurfaceResourceSelection.statusText(resource)
                .contains("Family: Loose stone"));
        assertTrue(ObservedSurfaceResourceSelection.statusText(resource)
                .contains("Registry variants: 1"));
    }

    @Test
    void preservesMultipleSelectionsByQualifiedKey() {
        ObservedSurfaceResourceCatalog catalog = observed.build(
                candidates.build(Map.of(
                        1, new BlockInfo(1, "game:loosestones-something-free"),
                        2, new BlockInfo(2, "somemod:loosestones-something-free"))),
                scan(
                        surface(0, 1),
                        surface(1, 2)
                ));

        assertEquals(List.of("game:something", "somemod:something"),
                ObservedSurfaceResourceSelection.preserveAll(
                        Set.of("game:something", "somemod:something", "game:missing"),
                        catalog.resources()).stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey()).toList());
    }

    private ObservedSurfaceResourceCatalog catalog() {
        var candidateCatalog = candidates.build(Map.of(
                1, new BlockInfo(1, "game:loosestones-obsidian-free"),
                2, new BlockInfo(2, "game:loosestones-nativecopper-free")
        ));
        return observed.build(
                candidateCatalog,
                scan(
                        surface(0, 1),
                        surface(1, 2)
                )
        );
    }

    private SurfaceObjectCompactFixtures.Observation surface(
            int x,
            int blockId
    ) {
        return observation(x, 0, 0, blockId);
    }
}
