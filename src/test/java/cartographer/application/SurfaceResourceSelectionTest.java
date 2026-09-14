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
        assertEquals(observed, SurfaceResourceSelection.observed(observed).observedResource().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new SurfaceResourceSelection(
                Optional.of(material), Optional.of(observed)));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceResourceSelection(
                Optional.empty(), Optional.empty()));
    }
}
