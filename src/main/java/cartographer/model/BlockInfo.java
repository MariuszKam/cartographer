package cartographer.model;

import java.util.Locale;

public record BlockInfo(
        int id,
        String code
) {
    private static final String[] FOLIAGE_FRAGMENTS = {
            "leaves",
            "foliage",
            "flower",
            "mushroom",
            "sapling",
            "crop",
            "tallgrass",
            "fern",
            "wildvine",
            "fruitingbush",
            "berrybush",
            "tallplant",
            "cattail",
            "reed",
            "bamboo",
            "cactus",
            "log-grown"
    };

    public static BlockInfo unknown(int id) {
        return new BlockInfo(
                id,
                "unknown:" + id
        );
    }

    public boolean isAir() {
        String normalized =
                normalizedCode();

        return id == 0
                || normalized.equals("air")
                || normalized.equals("game:air")
                || normalized.endsWith(":air");
    }

    /**
     * Blocks that should normally be ignored when the goal is to find
     * the underlying terrain surface.
     * Forest-floor blocks are intentionally NOT foliage. They are part
     * of the actual ground surface.
     */
    public boolean isFoliage() {
        String normalized =
                normalizedCode();

        if (isAir()) {
            return false;
        }

        return containsFoliageFragment(normalized);
    }

    private boolean containsFoliageFragment(String value) {
        for (String fragment : FOLIAGE_FRAGMENTS) {
            if (value.contains(fragment)) {
                return true;
            }
        }

        return false;
    }

    private String normalizedCode() {
        return code == null
                ? ""
                : code.toLowerCase(
                Locale.ROOT
        );
    }
}