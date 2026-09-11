package cartographer.render;

public class TerrainPalette {
    public int unknown() {
        return 0xFF2B2B2B;
    }

    public int background() {
        return 0xFF111111;
    }

    public int background(RenderStyle style) {
        return switch (style) {
            case SIMPLE -> 0xFF111111;
            case TOPOGRAPHIC -> 0xFF16130F;
            case HIGH_CONTRAST -> 0xFF000000;
        };
    }

    public int tileColor(int argb, int height, RenderStyle style) {
        return switch (style) {
            case SIMPLE -> argb;
            case TOPOGRAPHIC -> shade(argb, Math.max(0.65, Math.min(1.35, 0.85 + height / 256.0)));
            case HIGH_CONTRAST -> highContrast(argb);
        };
    }

    public int heightColor(int height, RenderStyle style) {
        int base =
                ground(height);

        return switch (style) {
            case SIMPLE -> base;
            case TOPOGRAPHIC -> shade(base, Math.max(0.65, Math.min(1.35, 0.85 + height / 256.0)));
            case HIGH_CONTRAST -> height > 96 ? 0xFFFFFFFF : 0xFF202020;
        };
    }

    public int water() {
        return 0xFF2D6FA3;
    }

    public int ground(int height) {
        int shade = Math.max(60, Math.min(180, 90 + height / 3));
        return 0xFF000000 | (shade / 2 << 16) | (shade << 8) | (shade / 3);
    }

    public int rock(int height) {
        int shade = Math.max(70, Math.min(210, 100 + height / 4));
        return 0xFF000000 | (shade << 16) | (shade << 8) | shade;
    }

    private int shade(int argb, double factor) {
        int alpha = argb & 0xFF000000;
        int red = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int green = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int blue = Math.min(255, (int) ((argb & 0xFF) * factor));
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private int highContrast(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance > 120 ? 0xFFFFFFFF : 0xFF202020;
    }
}
