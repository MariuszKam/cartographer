package cartographer.resource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectPresentationTest {
    @Test
    void mapsFamiliesToStableUserFacingLabels() {
        assertEquals("Ore bits", SurfaceObjectPresentation.familyLabel(SurfaceObjectFamily.ORE_BITS));
        assertEquals("Flint", SurfaceObjectPresentation.familyLabel(SurfaceObjectFamily.FLINT));
        assertEquals("Loose stone", SurfaceObjectPresentation.familyLabel(SurfaceObjectFamily.LOOSE_STONE));
        assertEquals("Loose boulder", SurfaceObjectPresentation.familyLabel(SurfaceObjectFamily.LOOSE_BOULDER));
        assertEquals("Ore bits, Loose stone", SurfaceObjectPresentation.familyLabels(
                List.of(SurfaceObjectFamily.LOOSE_STONE, SurfaceObjectFamily.ORE_BITS)));
    }

    @Test
    void keepsModNamespaceInDisplayIdentity() {
        SurfaceObjectCandidate candidate = new SurfaceObjectCandidate(
                "somemod", "obsidian", "Obsidian",
                java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(
                        List.of(SurfaceObjectFamily.LOOSE_STONE))),
                java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(List.of(7)))
        );

        assertEquals("Obsidian [somemod]", SurfaceObjectPresentation.displayName(candidate));
        assertEquals("somemod:obsidian", SurfaceObjectPresentation.qualifiedResourceKey(candidate));
        assertTrue(SurfaceObjectPresentation.dropdownLabel(
                new ObservedSurfaceResource(candidate, List.of(
                        new SurfaceObjectObservation(candidate, 1, 2, 3, 7))))
                .contains("Obsidian [somemod]"));
    }

    @Test
    void rejectsObjectAnalysisWithoutFamilies() {
        assertThrows(IllegalArgumentException.class, () -> new SurfaceObjectAnalysis(
                "Obsidian", "game:obsidian", 1, List.of(),
                new java.util.TreeSet<>()));
    }
}
