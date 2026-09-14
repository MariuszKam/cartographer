package cartographer.render;

import cartographer.resource.SurfaceObjectFamily;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceResourcePoint;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cartographer.model.HomeState;
import cartographer.model.WorldPosition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectMarkerStylePolicyTest {
    private final SurfaceObjectMarkerStylePolicy policy = new SurfaceObjectMarkerStylePolicy();

    @Test
    void mapsEachFamilyToItsRequiredShape() {
        assertEquals(SurfaceObjectMarkerShape.DIAMOND,
                policy.forFamilies(Set.of(SurfaceObjectFamily.ORE_BITS)).shape());
        assertEquals(SurfaceObjectMarkerShape.TRIANGLE,
                policy.forFamilies(Set.of(SurfaceObjectFamily.FLINT)).shape());
        assertEquals(SurfaceObjectMarkerShape.CIRCLE,
                policy.forFamilies(Set.of(SurfaceObjectFamily.LOOSE_STONE)).shape());
        assertEquals(SurfaceObjectMarkerShape.SQUARE,
                policy.forFamilies(Set.of(SurfaceObjectFamily.LOOSE_BOULDER)).shape());
    }

    @Test
    void usesMixedStyleForMultipleFamilies() {
        assertEquals(SurfaceObjectMarkerShape.MIXED,
                policy.forFamilies(Set.of(SurfaceObjectFamily.FLINT,
                        SurfaceObjectFamily.LOOSE_STONE)).shape());
    }

    @Test
    void boulderIsNotSmallerThanStone() {
        assertTrue(policy.forFamilies(Set.of(SurfaceObjectFamily.LOOSE_BOULDER)).radius()
                >= policy.forFamilies(Set.of(SurfaceObjectFamily.LOOSE_STONE)).radius());
    }

    @Test
    void countsStackedOccurrencesIndependently() {
        SurfaceObjectAnalysis analysis = new SurfaceObjectAnalysis(
                "Obsidian", "game:obsidian", 2,
                List.of(new SurfaceResourcePoint(0, 60, 0, "game:loose-obsidian"),
                        new SurfaceResourcePoint(0, 61, 0, "game:loose-obsidian")),
                new TreeSet<>(Set.of(SurfaceObjectFamily.LOOSE_STONE)));

        int drawn = new SurfaceResourceOverlayRenderer().drawObject(
                new BufferedImage(300, 120, BufferedImage.TYPE_INT_ARGB),
                new WorldPosition(0, 0, 0), 10, analysis, null, HomeState.absent());

        assertEquals(2, drawn);
        assertEquals(2, analysis.occurrenceCount());
    }
}
