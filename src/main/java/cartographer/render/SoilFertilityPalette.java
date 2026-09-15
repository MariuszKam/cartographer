package cartographer.render;

import cartographer.soil.SoilFertilityTier;

import java.awt.Color;

/**
 * Cartographer visualization colors for nominal soil fertility tiers.
 * These are not official Vintage Story map colors.
 */
public final class SoilFertilityPalette {
    private static final int ALPHA = 190;

    public Color color(SoilFertilityTier tier) {
        return switch (tier) {
            case BONY -> new Color(115, 115, 115, ALPHA);
            case BARREN -> new Color(105, 68, 36, ALPHA);
            case LOW -> new Color(184, 124, 48, ALPHA);
            case MEDIUM -> new Color(166, 178, 58, ALPHA);
            case HIGH -> new Color(56, 163, 76, ALPHA);
            case TERRA_PRETA -> new Color(22, 108, 58, ALPHA);
        };
    }
}
