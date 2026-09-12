package cartographer.render;

import java.awt.Color;

public final class OreOverlayPalette {

    private static final Color[] COLORS = {
            new Color(239, 91, 64),
            new Color(64, 170, 235),
            new Color(104, 205, 112),
            new Color(190, 113, 232),
            new Color(238, 190, 68),
            new Color(232, 112, 180),
            new Color(75, 207, 190),
            new Color(235, 142, 70)
    };

    private OreOverlayPalette() {
    }

    public static Color colorFor(String match, int stableIndex) {
        if (stableIndex < 0) {
            throw new IllegalArgumentException("stableIndex must be non-negative");
        }
        int index = Math.floorMod(
                match == null ? 0 : match.toLowerCase(java.util.Locale.ROOT).hashCode()
                        + stableIndex,
                COLORS.length
        );
        return COLORS[index];
    }
}
