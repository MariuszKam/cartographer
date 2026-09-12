package cartographer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrePresetTest {

    @Test
    void exposesStableDisplayLabelsAndMatches() {
        assertEquals("Native Copper", OrePreset.NATIVE_COPPER.label());
        assertEquals("nativecopper", OrePreset.NATIVE_COPPER.match());
        assertEquals("Tin / Cassiterite", OrePreset.TIN_CASSITERITE.label());
        assertEquals("cassiterite", OrePreset.TIN_CASSITERITE.match());
    }
}
