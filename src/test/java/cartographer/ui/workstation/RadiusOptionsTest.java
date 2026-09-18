package cartographer.ui.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadiusOptionsTest {
    @Test
    void exposesPerformanceRadiusLadderThroughR4096() {
        assertEquals(
                java.util.List.of(128, 256, 512, 1024, 2048, 4096),
                RadiusPane.supportedRadii()
        );
    }

    @Test
    void largeRadiusWarningsExplain4kRasterCap() {
        assertTrue(RadiusPane.warningTextFor(2048).contains("4096×4096"));
        assertTrue(RadiusPane.warningTextFor(4096).contains("~2 blocks/pixel"));
        assertEquals("", RadiusPane.warningTextFor(512));
    }
}
