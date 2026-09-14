package cartographer.render;

import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectFamily;
import cartographer.resource.SurfaceResourcePoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceOverlayLegendTest {
    @Test
    void objectLegendUsesOccurrencesAndNoDepositCenters() {
        SurfaceOverlayLegend legend = SurfaceOverlayLegend.forAnalysis(
                new SurfaceObjectAnalysis("Obsidian", "game:obsidian", 2,
                        List.of(new SurfaceResourcePoint(1, 2, 3, "game:loose-obsidian")),
                        new TreeSet<>(Set.of(SurfaceObjectFamily.LOOSE_STONE))));

        assertTrue(legend.title().contains("Surface object"));
        assertTrue(legend.metrics().contains("Occurrences: 1"));
        assertTrue(legend.metrics().contains("Variants: 2"));
        assertTrue(legend.metrics().contains("Family: Loose stone"));
        assertFalse(legend.metrics().stream().anyMatch(line -> line.contains("Deposits")));
        assertFalse(legend.depositCenters());
    }

    @Test
    void materialLegendUsesMaterialMetricsAndDepositCenters() {
        SurfaceOverlayLegend legend = SurfaceOverlayLegend.forAnalysis(
                new SurfaceMaterialAnalysis("Clay", 4, List.of(), List.of()));

        assertTrue(legend.title().contains("Surface material"));
        assertTrue(legend.metrics().contains("Matched blocks: 0"));
        assertTrue(legend.metrics().contains("Areas/deposits: 0"));
        assertTrue(legend.depositCenters());
    }
}
