package cartographer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SurfaceMaterialPresetTest {
    @Test
    void containsOnlySurfaceMaterials() {
        assertArrayEquals(
                new SurfaceMaterialPreset[] {
                        SurfaceMaterialPreset.FIRE_CLAY,
                        SurfaceMaterialPreset.CLAY,
                        SurfaceMaterialPreset.PEAT
                },
                SurfaceMaterialPreset.values()
        );
    }
}
