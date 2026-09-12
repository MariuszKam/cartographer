package cartographer.render;

import cartographer.model.SurfaceClass;

/**
 * Cartographer semantic terrain palette.
 * These are deliberately NOT claimed to be official Vintage Story
 * minimap colors.
 */
public class SemanticTerrainPalette {

    public int color(
            SurfaceClass surfaceClass,
            double hillshade
    ) {
        int base =
                switch (surfaceClass) {
                    case WATER ->
                            0xFF2F6F98;

                    case GRASS ->
                            0xFF6F9144;

                    case FOREST_FLOOR ->
                            0xFF5D6D3E;

                    case VEGETATION ->
                            0xFF3F783A;

                    case SOIL ->
                            0xFF896440;

                    case ROCK ->
                            0xFF888781;

                    case SAND ->
                            0xFFD4C28B;

                    case GRAVEL ->
                            0xFFA39A8C;

                    case SNOW ->
                            0xFFE8ECEA;

                    /*
                     * UNKNOWN is intentionally obvious.
                     *
                     * We want incorrect / unsupported classification
                     * to be visible instead of silently looking valid.
                     */
                    case UNKNOWN ->
                            0xFFB54CC2;
                };

        double factor = getFactor(surfaceClass, hillshade);

        return shade(
                base,
                factor
        );
    }

    private static double getFactor(SurfaceClass surfaceClass, double hillshade) {
        double effectiveHillshade =
                switch (surfaceClass) {
                    case WATER ->
                            hillshade * 0.20;

                    case SNOW ->
                            hillshade * 0.45;

                    default ->
                            hillshade;
                };

        return Math.clamp(
                1.0 + effectiveHillshade
                ,
                0.68,
                1.32);
    }

    private int shade(
            int argb,
            double factor
    ) {
        int alpha =
                argb & 0xFF000000;

        int red =
                Math.clamp(
                        (int) (
                                ((argb >> 16) & 0xFF)
                                        * factor
                        )
                        ,
                        0,
                        255);

        int green =
                Math.clamp(
                        (int) (
                                ((argb >> 8) & 0xFF)
                                        * factor
                        )
                        ,
                        0,
                        255);

        int blue =
                Math.clamp(
                        (int) (
                                (argb & 0xFF)
                                        * factor
                        )
                        ,
                        0,
                        255);

        return alpha
                | (red << 16)
                | (green << 8)
                | blue;
    }
}