package cartographer.render;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/** Deterministic color policy for logical observed surface-object resources. */
public final class SurfaceObjectColorPolicy {
    private static final List<Color> PALETTE = List.of(
            new Color(255, 80, 210, 220),
            new Color(80, 220, 255, 220),
            new Color(255, 190, 70, 220),
            new Color(150, 110, 255, 220),
            new Color(90, 235, 150, 220),
            new Color(255, 110, 100, 220)
    );

    private SurfaceObjectColorPolicy() {
    }

    public static Color colorFor(String qualifiedResourceKey) {
        Objects.requireNonNull(qualifiedResourceKey, "qualified resource key is required");
        return PALETTE.get(Math.floorMod(qualifiedResourceKey.hashCode(), PALETTE.size()));
    }
}
