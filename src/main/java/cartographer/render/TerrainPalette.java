package cartographer.render;

public class TerrainPalette {

    public int background(
            RenderStyle style
    ) {
        return switch (style) {
            case SIMPLE ->
                    0xFF111111;

            case TOPOGRAPHIC ->
                    0xFF16130F;

            case HIGH_CONTRAST ->
                    0xFF000000;
        };
    }

    public int terrainColor(
            int height,
            int minHeight,
            int maxHeight,
            double hillshade,
            RenderStyle style
    ) {
        double normalized =
                normalize(
                        height,
                        minHeight,
                        maxHeight
                );

        int base =
                switch (style) {
                    case SIMPLE ->
                            gradient(
                                    normalized,
                                    47,
                                    82,
                                    35,
                                    183,
                                    171,
                                    92
                            );

                    case TOPOGRAPHIC ->
                            gradient(
                                    normalized,
                                    37,
                                    80,
                                    43,
                                    226,
                                    215,
                                    163
                            );

                    case HIGH_CONTRAST ->
                            normalized >= 0.5
                                    ? 0xFFFFFFFF
                                    : 0xFF151515;
                };

        double contrast =
                switch (style) {
                    case SIMPLE ->
                            0.80;

                    case TOPOGRAPHIC ->
                            1.15;

                    case HIGH_CONTRAST ->
                            1.35;
                };

        double factor =
                clamp(
                        0.35,
                        1.70,
                        0.78
                                + normalized
                                * 0.42
                                + hillshade
                                * contrast
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
                argb
                        & 0xFF000000;

        int red =
                Math.min(
                        255,
                        (int) (
                                ((argb >> 16)
                                        & 0xFF)
                                        * factor
                        )
                );

        int green =
                Math.min(
                        255,
                        (int) (
                                ((argb >> 8)
                                        & 0xFF)
                                        * factor
                        )
                );

        int blue =
                Math.min(
                        255,
                        (int) (
                                (argb
                                        & 0xFF)
                                        * factor
                        )
                );

        return alpha
                | (red << 16)
                | (green << 8)
                | blue;
    }

    private double normalize(
            int height,
            int minHeight,
            int maxHeight
    ) {
        if (maxHeight <= minHeight) {
            return 0.5;
        }

        return clamp(
                0.0,
                1.0,
                (height - minHeight)
                        / (double) (
                        maxHeight - minHeight
                )
        );
    }

    private int gradient(
            double amount,
            int lowRed,
            int lowGreen,
            int lowBlue,
            int highRed,
            int highGreen,
            int highBlue
    ) {
        int red =
                interpolate(
                        lowRed,
                        highRed,
                        amount
                );

        int green =
                interpolate(
                        lowGreen,
                        highGreen,
                        amount
                );

        int blue =
                interpolate(
                        lowBlue,
                        highBlue,
                        amount
                );

        return 0xFF000000
                | (red << 16)
                | (green << 8)
                | blue;
    }

    private int interpolate(
            int low,
            int high,
            double amount
    ) {
        return (int) Math.round(
                low
                        + (high - low)
                        * clamp(
                        0.0,
                        1.0,
                        amount
                )
        );
    }

    private double clamp(
            double min,
            double max,
            double value
    ) {
        return Math.clamp(
                value,
                min,
                max
        );
    }
}