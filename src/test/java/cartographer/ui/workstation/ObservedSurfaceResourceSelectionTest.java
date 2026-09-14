package cartographer.ui.workstation;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.ObservedSurfaceResourceCatalogBuilder;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
                List.of(
                        new SurfaceBlock(0, 0, 0,
                                new BlockInfo(1, "game:loosestones-something-free")),
                        new SurfaceBlock(1, 0, 0,
                                new BlockInfo(2, "somemod:loosestones-something-free"))
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

    private ObservedSurfaceResourceCatalog catalog() {
        var candidateCatalog = candidates.build(Map.of(
                1, new BlockInfo(1, "game:loosestones-obsidian-free"),
                2, new BlockInfo(2, "game:loosestones-nativecopper-free")
        ));
        return observed.build(candidateCatalog, List.of(
                new SurfaceBlock(0, 0, 0,
                        new BlockInfo(1, "game:loosestones-obsidian-free")),
                new SurfaceBlock(1, 0, 0,
                        new BlockInfo(2, "game:loosestones-nativecopper-free"))
        ));
    }
}
