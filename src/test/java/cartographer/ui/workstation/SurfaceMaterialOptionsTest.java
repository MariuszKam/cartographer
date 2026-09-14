package cartographer.ui.workstation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cartographer.ui.SurfaceResourcePreset;
import org.junit.jupiter.api.Test;

class SurfaceMaterialOptionsTest {
    @Test
    void legacyMaterialsRemainAvailableWithoutLegacyObsidian() {
        var materials = SearchPanel.legacySurfaceMaterials();

        assertEquals(java.util.List.of(SurfaceResourcePreset.FIRE_CLAY,
                SurfaceResourcePreset.CLAY, SurfaceResourcePreset.PEAT), materials);
        assertFalse(materials.contains(SurfaceResourcePreset.OBSIDIAN));
    }
}
