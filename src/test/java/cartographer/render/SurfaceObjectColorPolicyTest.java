package cartographer.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SurfaceObjectColorPolicyTest {
    @Test
    void isStableAndNamespaceAware() {
        Color game = SurfaceObjectColorPolicy.colorFor("game:obsidian");
        assertEquals(game, SurfaceObjectColorPolicy.colorFor("game:obsidian"));
        assertNotEquals(game, SurfaceObjectColorPolicy.colorFor("game:nativecopper"));
    }
}
