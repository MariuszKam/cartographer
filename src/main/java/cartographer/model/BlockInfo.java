package cartographer.model;

import java.util.Locale;

public record BlockInfo(
        int id,
        String code
) {

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

        return containsAny(
                normalized,
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
        );
    }

    public String materialType() {
        String normalized =
                normalizedCode();

        if (isAir()) {
            return "air";
        }

        if (normalized.contains("water")) {
            return "water";
        }

        if (normalized.contains("lava")) {
            return "lava";
        }

        if (normalized.contains("snow")) {
            return "snow";
        }

        if (normalized.contains("forestfloor")) {
            return "ground";
        }

        if (isRockLike(normalized)) {
            return "rock";
        }

        if (normalized.contains("gravel")) {
            return "gravel";
        }

        if (normalized.contains("sand")) {
            return "sand";
        }

        if (containsAny(
                normalized,
                "soil",
                "clay",
                "peat",
                "mud"
        )) {
            return "ground";
        }

        if (containsAny(
                normalized,
                "wood",
                "log",
                "plank"
        )) {
            return "wood";
        }

        if (isFoliage()) {
            return "foliage";
        }

        return "solid";
    }

    private boolean isRockLike(
            String normalized
    ) {
        return containsAny(
                normalized,
                "rock",
                "stone",
                "ore",
                "flint",
                "stalag",
                "stalact",
                "looseboulder"
        );
    }

    private boolean containsAny(
            String value,
            String... fragments
    ) {
        for (String fragment : fragments) {
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