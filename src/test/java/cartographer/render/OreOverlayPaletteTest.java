package cartographer.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OreOverlayPaletteTest {

    @Test
    void assignsDeterministicColors() {
        Color first = OreOverlayPalette.colorFor("nativecopper", 0);
        Color second = OreOverlayPalette.colorFor("nativecopper", 0);

        assertEquals(first, second);
    }
}
