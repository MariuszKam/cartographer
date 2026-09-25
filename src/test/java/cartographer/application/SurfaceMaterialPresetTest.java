package cartographer.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceMaterialPresetTest {
    @Test
    void supportedMaterialResolverRejectsObjectAndUnknownNames() {
        assertTrue(SurfaceMaterialPreset.resolve("Fire Clay").isPresent());
        assertTrue(SurfaceMaterialPreset.resolve("clay").isPresent());
        assertTrue(SurfaceMaterialPreset.resolve("PEAT").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("obsidian").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("nativecopper").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("cassiterite").isPresent());
        assertFalse(
                SurfaceMaterialPreset.resolve(
                        "arbitrary-unknown-value"
                ).isPresent()
        );
    }

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
