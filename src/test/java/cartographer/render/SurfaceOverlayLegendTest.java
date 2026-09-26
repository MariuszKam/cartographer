package cartographer.render;

import cartographer.resource.SurfaceMaterialAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceOverlayLegendTest {
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
