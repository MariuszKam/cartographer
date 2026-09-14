package cartographer.ui.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cartographer.ui.SurfaceMaterialPreset;
import org.junit.jupiter.api.Test;

class SurfaceMaterialOptionsTest {
    @Test
    void legacyMaterialsRemainAvailableWithoutLegacyObsidian() {
        var materials = SearchPanel.surfaceMaterials();

        assertEquals(java.util.List.of(SurfaceMaterialPreset.FIRE_CLAY,
                SurfaceMaterialPreset.CLAY, SurfaceMaterialPreset.PEAT), materials);
        assertEquals(3, SurfaceMaterialPreset.values().length);
    }
}
