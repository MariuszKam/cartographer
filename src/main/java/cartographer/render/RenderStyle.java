package cartographer.render;

import java.util.Locale;

public enum RenderStyle {
    SIMPLE,
    TOPOGRAPHIC,
    HIGH_CONTRAST;

    public static RenderStyle parse(String value) {
        if (value == null || value.isBlank()) {
            return SIMPLE;
        }
        return RenderStyle.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
