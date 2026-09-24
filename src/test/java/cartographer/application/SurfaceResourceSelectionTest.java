package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.SurfaceObjectCandidate;
import cartographer.resource.SurfaceObjectCandidateCatalogBuilder;
import cartographer.resource.SurfaceObjectObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurfaceResourceSelectionTest {
    @Test
    void acceptsExactlyOneSelectionKind() {
        SurfaceMaterialMatch material = new SurfaceMaterialMatch("Clay", List.of("clay"));
        SurfaceObjectCandidate candidate = new SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(1, new BlockInfo(1, "game:loosestones-obsidian-free")))
                .findByBlockId(1)
                .orElseThrow();
        ObservedSurfaceResource observed = new ObservedSurfaceResource(
                candidate,
                List.of(new SurfaceObjectObservation(candidate, 1, 2, 3, 1))
        );

        assertEquals(material, SurfaceResourceSelection.material(material).material().orElseThrow());
        assertEquals(List.of(observed), SurfaceResourceSelection.observedResources(List.of(observed)).observedResources());
        assertThrows(IllegalArgumentException.class, () -> new SurfaceResourceSelection(
                Optional.of(material), List.of(observed)));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceResourceSelection(
                Optional.empty(), List.of()));
    }

    @Test
    void acceptsMultipleDistinctObservedResourcesAndRejectsDuplicates() {
        BlockInfo obsidianBlock = new BlockInfo(1, "game:loosestones-obsidian-free");
        BlockInfo copperBlock = new BlockInfo(2, "game:looseores-nativecopper-granite-free");
        var catalog = new cartographer.resource.SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(1, obsidianBlock, 2, copperBlock));
        ObservedSurfaceResource obsidian = new ObservedSurfaceResource(
                catalog.findByBlockId(1).orElseThrow(),
                List.of(new SurfaceObjectObservation(catalog.findByBlockId(1).orElseThrow(), 1, 2, 3, 1)));
        ObservedSurfaceResource copper = new ObservedSurfaceResource(
                catalog.findByBlockId(2).orElseThrow(),
                List.of(new SurfaceObjectObservation(catalog.findByBlockId(2).orElseThrow(), 4, 5, 6, 2)));

        SurfaceResourceSelection selection = SurfaceResourceSelection.observedResources(
                List.of(obsidian, copper));

        assertEquals(List.of("game:nativecopper", "game:obsidian"), selection.observedResources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey()).toList());
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceResourceSelection.observedResources(List.of(obsidian, obsidian)));
    }
}
