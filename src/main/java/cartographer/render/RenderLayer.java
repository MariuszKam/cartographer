package cartographer.render;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum RenderLayer {
    TERRAIN,
    WATER,
    SURFACE,
    MARKERS;

    public static Set<RenderLayer> defaults() {
        return EnumSet.of(TERRAIN, WATER, MARKERS);
    }

    public static Set<RenderLayer> parse(String value) {
        if (value == null || value.isBlank()) {
            return defaults();
        }
        EnumSet<RenderLayer> layers = EnumSet.noneOf(RenderLayer.class);
        for (String token : value.split(",")) {
            layers.add(RenderLayer.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        }
        return layers;
    }
}
