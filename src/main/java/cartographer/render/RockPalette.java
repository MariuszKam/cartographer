package cartographer.render;

import cartographer.geology.rock.RockIdentity;

import java.awt.Color;
import java.util.Map;
import java.util.Objects;

public final class RockPalette {
    private static final Map<String, Integer> KNOWN_COLORS = Map.of(
            "game:rock-granite", 0xFF8B8B8B,
            "game:rock-basalt", 0xFF4F555A,
            "game:rock-limestone", 0xFFD8CFA8,
            "game:rock-sandstone", 0xFFC89455,
            "game:rock-shale", 0xFF687A86,
            "game:rock-slate", 0xFF4D5968,
            "game:rock-andesite", 0xFF717B78,
            "game:rock-marble", 0xFFE4E1D8,
            "game:rock-chalk", 0xFFD9D7C7,
            "game:rock-chert", 0xFF9B735F
    );

    public int colorFor(RockIdentity identity) {
        Objects.requireNonNull(identity, "rock identity is required");
        return KNOWN_COLORS.getOrDefault(
                identity.code(),
                fallbackColor(identity.code())
        );
    }

    private int fallbackColor(String code) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < code.length(); index++) {
            hash ^= code.charAt(index);
            hash *= 0x100000001B3L;
        }

        float hue = (hash & 0xFFFFFFFFL) / (float) 0x1_0000_0000L;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.62f, 0.78f) & 0x00FFFFFF);
    }
}
