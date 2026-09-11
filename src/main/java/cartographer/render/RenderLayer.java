package cartographer.render;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum RenderLayer {
    TERRAIN,
    SURFACE,
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

            /*
             * Backward-compatible CLI alias.
             *
             * Water is now decoded from the real liquid layer and is
             * therefore part of SURFACE rather than a separate render layer.
             */
            if ("WATER".equals(normalized)) {
                layers.add(
                        SURFACE
                );

                continue;
            }

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