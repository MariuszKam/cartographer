package cartographer.render;

import cartographer.model.SurfaceClass;

public class SemanticTerrainPalette {
    public int color(
            SurfaceClass surfaceClass,
            double hillshade
    ) {
        int base =
                switch (surfaceClass) {
                    case WATER -> 0xFF2D6FA3;
                    case GRASS -> 0xFF4F8F3A;
                    case VEGETATION -> 0xFF2F6E2D;
                    case SOIL -> 0xFF7C5A36;
                    case ROCK -> 0xFF777777;
                    case SAND -> 0xFFD7C27A;
                    case GRAVEL -> 0xFF9B9284;
                    case SNOW -> 0xFFECECEC;
                    case UNKNOWN -> 0xFF8050A0;
                };

        double factor =
                Math.max(
                        0.60,
                        Math.min(
                                1.45,
                                1.0 + hillshade
                        )
                );

        return shade(
                base,
                factor
        );
    }

    private int shade(
            int argb,
            double factor
    ) {
        int alpha =
                argb & 0xFF000000;

        int red =
                Math.min(
                        255,
                        (int) (((argb >> 16) & 0xFF) * factor)
                );

        int green =
                Math.min(
                        255,
                        (int) (((argb >> 8) & 0xFF) * factor)
                );

        int blue =
                Math.min(
                        255,
                        (int) ((argb & 0xFF) * factor)
                );

        return alpha
                | (red << 16)
                | (green << 8)
                | blue;
    }
}
