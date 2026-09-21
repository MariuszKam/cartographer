package cartographer.resource;

import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceObjectCompactFixtures;
import cartographer.ui.workstation.SurfaceObjectDiscoveryState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cartographer.scanner.SurfaceObjectCompactFixtures.observation;
import static cartographer.scanner.SurfaceObjectCompactFixtures.scan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectFilterTest {
    @Test
    void blankSearchShowsResourcesInSourceOrder() {
        List<ObservedSurfaceResource> result = SurfaceObjectFilter.visibleResources(
                resources(), "  ", Set.of());

        assertEquals(List.of("game:nativecopper", "game:obsidian", "somemod:obsidian"), keys(result));
    }

    @Test
    void searchMatchesIdentityNamespaceAndFamilyCaseInsensitively() {
        assertEquals(List.of("game:nativecopper"), keys(visible("COPPER", Set.of())));
        assertEquals(List.of("game:obsidian", "somemod:obsidian"), keys(visible("OBSIDIAN", Set.of())));
        assertEquals(List.of("somemod:obsidian"), keys(visible("somemod", Set.of())));
        assertEquals(List.of("game:nativecopper"), keys(visible("ore bits", Set.of())));
        assertEquals(List.of("game:obsidian", "somemod:obsidian"), keys(visible("loose stone", Set.of())));
    }

    @Test
    void familyFiltersUseOrAndCombineWithSearchUsingAnd() {
        assertEquals(List.of("game:nativecopper"), keys(visible("", Set.of(SurfaceObjectFamily.ORE_BITS))));
        assertEquals(List.of("game:nativecopper", "game:obsidian", "somemod:obsidian"),
                keys(visible("", Set.of(SurfaceObjectFamily.ORE_BITS, SurfaceObjectFamily.LOOSE_STONE))));
        assertEquals(List.of(), keys(visible("copper", Set.of(SurfaceObjectFamily.LOOSE_STONE))));
    }

    @Test
    void multiFamilyResourceMatchesEachOfItsEnabledFamilies() {
        SurfaceObjectCandidateCatalog catalog = new SurfaceObjectCandidateCatalogBuilder().build(Map.of(
                1, block("game:looseores-nativecopper-granite-free"),
                2, block("game:loosestones-nativecopper-free")));
        List<ObservedSurfaceResource> resources =
                new ObservedSurfaceResourceCatalogBuilder().build(
                        catalog,
                        scan(
                                surface(1),
                                surface(2)
                        )
                ).resources();

        assertEquals(List.of("game:nativecopper"), keys(SurfaceObjectFilter.visibleResources(
                resources, "", Set.of(SurfaceObjectFamily.LOOSE_STONE))));
    }

    @Test
    void selectionActionsChangeOnlyVisibleKeys() {
        List<ObservedSurfaceResource> all = resources();
        List<ObservedSurfaceResource> visible = List.of(all.getFirst());

        Set<String> selected = Set.of("game:obsidian");
        assertEquals(Set.of("game:nativecopper", "game:obsidian"),
                SurfaceObjectSelectionActions.selectVisible(selected, visible));
        assertEquals(Set.of("game:obsidian"),
                SurfaceObjectSelectionActions.clearVisible(
                        Set.of("game:nativecopper", "game:obsidian"), visible));
        assertEquals(Set.of(), SurfaceObjectSelectionActions.clearAll());
    }

    @Test
    void noVisibleResourcesDoNotClearSelectedResourcesOrDisableRender() {
        Set<String> selectedBefore = Set.of("game:nativecopper", "game:obsidian");
        List<ObservedSurfaceResource> visible = SurfaceObjectFilter.visibleResources(
                resources(), "does-not-match", Set.of());
        Set<String> selectedAfter = SurfaceObjectSelectionActions.clearVisible(selectedBefore, visible);

        assertEquals(List.of(), visible);
        assertEquals(selectedBefore, selectedAfter);
        assertTrue(SurfaceObjectDiscoveryState.READY.allowsRender(false, !selectedAfter.isEmpty()));
    }

    @Test
    void resettingFiltersLeavesSelectionStateUntouched() {
        SurfaceObjectFilterState before = new SurfaceObjectFilterState(
                "  copper  ", Set.of(SurfaceObjectFamily.ORE_BITS));
        Set<String> selected = Set.of("game:nativecopper", "game:obsidian");
        SurfaceObjectFilterState after = before.reset();
        Set<String> selectedAfter = SurfaceObjectSelectionActions.selectVisible(selected, List.of());

        assertEquals("", after.searchText());
        assertEquals(Set.of(), after.enabledFamilies());
        assertEquals(selected, selectedAfter);
    }

    private List<ObservedSurfaceResource> visible(String search, Set<SurfaceObjectFamily> families) {
        return SurfaceObjectFilter.visibleResources(resources(), search, families);
    }

    private List<String> keys(List<ObservedSurfaceResource> resources) {
        return resources.stream().map(resource -> resource.candidate().qualifiedResourceKey()).toList();
    }

    private List<ObservedSurfaceResource> resources() {
        SurfaceObjectCandidateCatalog catalog = new SurfaceObjectCandidateCatalogBuilder().build(Map.of(
                1, block("game:looseores-nativecopper-granite-free"),
                2, block("game:loosestones-obsidian-free"),
                3, block("somemod:loosestones-obsidian-free")));
        return new ObservedSurfaceResourceCatalogBuilder().build(
                catalog,
                scan(
                        surface(1),
                        surface(2),
                        surface(3)
                )
        ).resources();
    }

    private SurfaceObjectCompactFixtures.Observation surface(int id) {
        return observation(id, 0, id, id);
    }

    private BlockInfo block(String code) {
        return new BlockInfo(0, code);
    }
}
