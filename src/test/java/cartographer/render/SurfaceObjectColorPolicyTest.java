package cartographer.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SurfaceObjectColorPolicyTest {
    @Test
    void isStableAndNamespaceAware() {
        Color game = SurfaceObjectColorPolicy.colorFor("game:obsidian");
        assertEquals(game, SurfaceObjectColorPolicy.colorFor("game:obsidian"));
        assertNotEquals(game, SurfaceObjectColorPolicy.colorFor("somemod:obsidian"));
    }

    @Test
    void commonResourceKeysDoNotCollapseToTheSameColor() {
        List<Color> colors = List.of(
                SurfaceObjectColorPolicy.colorFor("game:obsidian"),
                SurfaceObjectColorPolicy.colorFor("game:nativecopper"),
                SurfaceObjectColorPolicy.colorFor("game:cassiterite"),
                SurfaceObjectColorPolicy.colorFor("game:flint")
        );

        for (int first = 0; first < colors.size(); first++) {
            for (int second = first + 1; second < colors.size(); second++) {
                assertNotEquals(colors.get(first), colors.get(second));
            }
        }
    }
}
