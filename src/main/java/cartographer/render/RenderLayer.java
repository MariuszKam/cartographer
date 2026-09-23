package cartographer.render;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum RenderLayer {
    TERRAIN,
    SURFACE,
    SOIL_FERTILITY,
    ENVIRONMENT,
    GEOLOGY,
    MARKERS;

    public static Set<RenderLayer> defaults() {
        return EnumSet.of(
                TERRAIN,
                SURFACE,
                MARKERS
        );
    }

    public static Set<RenderLayer> parse(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return defaults();
        }

        EnumSet<RenderLayer> layers =
                EnumSet.noneOf(
                        RenderLayer.class
                );

        for (String token : value.split(",")) {
            String normalized =
                    token.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            );

            try {
                layers.add(
                        RenderLayer.valueOf(
                                normalized
                        )
                );

            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown render layer: "
                                + token.trim()
                );
            }
        }

        return layers;
    }
}
